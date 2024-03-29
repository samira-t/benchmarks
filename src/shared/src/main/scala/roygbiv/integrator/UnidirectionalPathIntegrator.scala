/**
  Copyright [2011] [Henrik Engstroem, Mario Gonzalez]

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/
package roygbiv.integrator

import roygbiv.color.RGBColor
import roygbiv.scene.Scene
import roygbiv.math.{Ray, MathUtils, Tuple3f}
import roygbiv.shape._
import roygbiv.sampler.Sampler

case class UnidirectionalPathIntegrator(scene: Scene, sampler: Sampler) {
  val camera = scene.camera
  var lightHit = false
  val numLights = scene.emitters.length
  val lightPdf = if (numLights > 0) 1.0f / numLights else 0.0f

  def l(pixelX: Float, pixelY: Float): RGBColor = {
    val color = tracePixel(pixelX, pixelY)
    if (!color.isNaN) color else RGBColor.Black
  }

  def tracePixel(pixelX: Float, pixelY: Float): RGBColor = {
    val ray = camera.getRayForPixel(pixelX, pixelY)

    scene.intersect(ray) match {
      case Some(i) => i.shape match {
        case s: Scatterer => {
          estimateRadiance(s, i.point, getAdjustedNormal(s.getNormalAtPoint(i.point), ray, s), -ray.direction, 1)
        }
        case e: Emitter => {
          if (e.getNormalAtPoint(i.point).dot(ray.direction) < 0.0f) e.le else RGBColor.Black
        }
      }
      case None => RGBColor.Black
    }
  }

  def estimateRadiance(shape: Scatterer, intersection: Tuple3f, normal: Tuple3f, wi: Tuple3f, traceDepth: Int): RGBColor = {
    if (traceDepth > UnidirectionalPathIntegrator.MaxTraceDepth)
      return RGBColor.Black

    var color = estimateIndirectIllumination(shape, intersection, normal, wi, traceDepth)

    if (!lightHit)
      color = color + estimateDirectIllumination(shape, intersection, normal, wi, traceDepth)
    else
      lightHit = false

    color
  }

  def estimateIndirectIllumination(shape: Scatterer, intersection: Tuple3f, normal: Tuple3f, wi: Tuple3f, traceDepth: Int): RGBColor = {
    val (u1, u2) = sampler.nextSample2D
    val sample = shape.material.bsdf.sampleF(wi, normal, u1, u2)

    if (sample.pdf == 0.0f)
      return RGBColor.Black

    val contrib = sample.color * (scala.math.abs(normal.dot(sample.wo)) / sample.pdf)

    // Russian roulette probability
    val rrProb = if (traceDepth > UnidirectionalPathIntegrator.MinBounces) scala.math.min(0.9f, contrib.max) else 1.0f
    var estimatedRadiance = RGBColor.Black

    if (rrProb > sampler.nextSample1D) {
      val ray = Ray(normal * MathUtils.Epsilon + intersection, sample.wo)

      scene.intersect(ray) match {
        case Some(i) => i.shape match {
          case s: Scatterer => {
            estimatedRadiance = estimateRadiance(s, i.point, getAdjustedNormal(s.getNormalAtPoint(i.point), ray, s), -ray.direction, traceDepth + 1)
          }
          case e: Emitter => {
            val newDot = e.getNormalAtPoint(i.point).dot(ray.direction)

            if (newDot < 0.0f) {
              lightHit = true

              if (shape.material.bsdf.hasDeltaDistribution) {
                estimatedRadiance = e.le
              } else {
                val dot = scala.math.abs(normal.dot(sample.wo))
                val lightCosine = scala.math.abs(newDot)
                val distanceSquared = intersection.distanceSquared(i.point)
                val transformedArea = 1.0f / e.pdf
                val lPdf = (distanceSquared / (lightCosine * transformedArea)) * lightPdf
                estimatedRadiance = weightedIllumination(dot, e.le, sample.color, lPdf, sample.pdf)
              }
            }
          }
        }
        case None => estimatedRadiance = RGBColor.Black
      }

      if (!estimatedRadiance.isBlack) {
        if (!lightHit)
          estimatedRadiance = estimatedRadiance * contrib

        // Russian roulette weighting
        if (traceDepth > UnidirectionalPathIntegrator.MinBounces)
          estimatedRadiance = estimatedRadiance * (1.0f / rrProb)
      }
    }

    estimatedRadiance
  }

  def estimateDirectIllumination(shape: Scatterer, intersection: Tuple3f, normal: Tuple3f, wi: Tuple3f, traceDepth: Int): RGBColor = {
    var retColor = RGBColor.Black

    if (numLights > 0 && !shape.material.bsdf.hasDeltaDistribution) {
      // Get a sample from a random light in the scene
      val emitter = scene.emitters(scala.math.round(sampler.nextSample1D * (numLights - 1)))
      val (u1, u2) = sampler.nextSample2D
      val sample = emitter.getSample(u1, u2)

      // Check visibility
      if (mutuallyVisible(intersection, normal, shape, sample)) {
        // Calculate distance (for probability densities later)
        var lightVec = intersection - sample.point
        val distanceSquared = lightVec.lengthSquared
        lightVec = lightVec.normalize

        // Calculate angles
        val lightCosine = scala.math.abs(sample.normal.dot(lightVec))
        val dot = scala.math.abs(normal.dot(lightVec))

        // Calculate pdfs
        val transformedArea = 1.0f / sample.pdf
        val lPdf = (distanceSquared / (lightCosine * transformedArea)) * lightPdf
        val bsdfPdf = scala.math.abs(shape.material.bsdf.pdf(wi, lightVec, normal))

        // Return the MIS weighted illumination
        retColor = weightedIllumination(dot, sample.l, shape.material.bsdf.f(wi, lightVec, normal), lPdf, bsdfPdf)
      }
    }

    retColor
  }

  def weightedIllumination(dot: Float, li: RGBColor, f: RGBColor, lightPdf: Float, bsdfPdf: Float): RGBColor = {
    var weightedColor = RGBColor.Black

    if (!f.isBlack) {
      val lPdf = scala.math.abs(lightPdf)
      val bPdf = scala.math.abs(bsdfPdf)
      val weight1 = MathUtils.powerHeuristic(lPdf, bPdf)
      weightedColor = li * f * ((dot * weight1) / lPdf)

      if (bPdf > 0.0f) {
        val weight2 = MathUtils.powerHeuristic(bPdf, lPdf)
        weightedColor = weightedColor + li * f * ((dot * weight2) / bPdf)
      }
    }

    weightedColor
  }

  def mutuallyVisible(intersection: Tuple3f, normal: Tuple3f, shape: Shape, sample: LightSample): Boolean = {
    val direction = intersection - sample.point
    var visible = false

    // Check relative normal orientations for early return
    if (!(sample.normal.dot(direction) < 0.0f || normal.dot(direction) > 0.0f)) {
      val origin = sample.normal * MathUtils.Epsilon + sample.point
      val ray = Ray(origin, direction.normalize)

      scene.intersect(ray) match {
        case Some(i) => if ((shape eq i.shape)) visible = true
        case None => visible = false
      }
    }

    visible
  }

  def getAdjustedNormal(normal: Tuple3f, ray: Ray, scatterer: Scatterer): Tuple3f = {
    if (!scatterer.material.bsdf.hasDeltaDistribution && normal.dot(ray.direction) > 0.0f) -normal else normal
  }
}

object UnidirectionalPathIntegrator {
  val MaxTraceDepth = 1000
  val MinBounces = 3
}