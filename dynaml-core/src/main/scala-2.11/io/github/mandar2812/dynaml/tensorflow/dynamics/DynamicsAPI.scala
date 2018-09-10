/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
* */
package io.github.mandar2812.dynaml.tensorflow.dynamics

import org.platanios.tensorflow.api._
import org.platanios.tensorflow.api.learn.Mode
import org.platanios.tensorflow.api.learn.layers.Layer
import org.platanios.tensorflow.api.ops.Output
import org.platanios.tensorflow.api.ops.variables.Initializer
import org.platanios.tensorflow.api.types.DataType

/**
  * <h3>Differential Operators & PDEs</h3>
  *
  * <i>Partial Differential Equations</i> (PDE) are the most important tool for
  * modelling physical processes and phenomena which vary in space and time.
  *
  * The dynamics package contains classes and implementations of PDE operators.
  *
  * */
private[tensorflow] trait DynamicsAPI {

  val jacobian: Gradient.type                         = Gradient
  val ∇ : Gradient.type                               = Gradient
  val hessian: TensorOperator[Output]                 = ∇(∇)
  val source: SourceOperator.type                     = SourceOperator
  val constant: Constant.type                         = Constant
  def one[I](
    dataType: DataType)(
    shape: Shape): Constant[I]                        = constant[I]("One", Tensor.ones(dataType, shape))

  def variable[I](
    name: String,
    dataType: DataType,
    shape: Shape,
    initializer: Initializer = null): SourceOperator[I] =
    source(
      name,
      new Layer[I, Output](name) {
        override val layerType: String = "Quantity"

        override protected def _forward(input: I)(implicit mode: Mode): Output = {
          val quantity = tf.variable(name = "value", dataType, shape, initializer)
          quantity
        }
      })


  /**
    * Calculate a `sliced` gradient.
    *
    * ∂f[slices]/∂x[slices]
    *
    * @param name A string identifier for the operator
    * @param input_slices Determines over which subset of the inputs to compute the gradient
    * @param output_slices Determines over which subset of the outputs to compute the gradient
    * */
  def ∂(
    name: String)(
    input_slices: Indexer*)(
    output_slices: Indexer*): SlicedGradient          = SlicedGradient(name)(input_slices:_*)(output_slices:_*)

  /**
    * Time derivative (∂f/∂t) of a function f(t, s),
    * which accepts space-time vectors [t, s_1, s_2, ..., s_n]
    * as inputs
    * */
  val d_t: SlicedGradient                             = ∂("D_t")(0)(---)

  /**
    * Space derivative (∂f/∂s) of a function f(t, s),
    * which accepts space-time vectors [t, s_1, s_2, ..., s_n]
    * as inputs
    * */
  val d_s: SlicedGradient                             = ∂("D_s")( 1 ::)(---)

}
