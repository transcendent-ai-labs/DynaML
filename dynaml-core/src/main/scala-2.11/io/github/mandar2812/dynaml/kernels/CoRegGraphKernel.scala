package io.github.mandar2812.dynaml.kernels

import breeze.linalg.{DenseMatrix, pinv}
import io.github.mandar2812.dynaml.utils

/**
  * @author mandar2812 date: 30/08/16.
  *
  * In co-regionalization models for multi-output gaussian processes,
  * one comes across the graph regularizer. the class below is an implementation
  * of such.
  *
  * @param m The symmetric adjacency matrix of the graph generated by the nodes indexed
  *          by integers.
  */
class CoRegGraphKernel(m: DenseMatrix[Double]) extends LocalSVMKernel[Int] {

  utils.isSquareMatrix(m)

  utils.isSymmetricMatrix(m)

  val dimensions = m.rows

  state = {
    for(i <- 0 until dimensions; j <- 0 until dimensions)
      yield (i,j)
  }.filter((coup) => coup._1 <= coup._2)
    .map(c => "M_"+c._1+"_"+c._2 -> m(c._1, c._2))
    .toMap

  override val hyper_parameters: List[String] = state.keys.toList

  def adjecancyMatrix(config: Map[String, Double]) = DenseMatrix.tabulate[Double](dimensions, dimensions){(i, j) =>
    if(i <= j) config("M_"+i+"_"+j)
    else config("M_"+j+"_"+i)
  }

  def degreeMatrix(config: Map[String, Double]) =
    DenseMatrix.eye[Double](dimensions) :*
      ((adjecancyMatrix(config) * DenseMatrix.ones[Double](dimensions, dimensions)) + adjecancyMatrix(config))

  def l(config: Map[String, Double]): DenseMatrix[Double] = pinv(degreeMatrix(config) - adjecancyMatrix(config))

  override def gradientAt(config: Map[String, Double])(x: Int, y: Int): Map[String, Double] =
    hyper_parameters.map(_ -> 1.0).toMap

  override def evaluateAt(config: Map[String, Double])(x: Int, y: Int): Double = l(config)(x,y)

  override def setHyperParameters(h: Map[String, Double]): CoRegGraphKernel.this.type = {
    super.setHyperParameters(h)
    this
  }
}
