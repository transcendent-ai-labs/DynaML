//Uncertainty Quantification Benchmarks
import breeze.linalg.DenseVector
import io.github.tailhq.dynaml.analysis.VectorField
import io.github.tailhq.dynaml.models.gp.GPRegression
import io.github.tailhq.dynaml.optimization.{CoupledSimulatedAnnealing, GridSearch}
import breeze.stats.distributions.Uniform
import io.github.tailhq.dynaml.kernels.{DiracKernel, MLPKernel, PeriodicKernel, RBFKernel}
import io.github.tailhq.dynaml.pipes.DataPipe
import io.github.tailhq.dynaml.probability._
import io.github.tailhq.dynaml.probability.distributions.MultivariateUniform
import spire.implicits._
import io.github.tailhq.dynaml.graphics.charts.Highcharts._


val num_features: Int = 1
implicit val ev = VectorField(num_features)

val xPrior = RandomVariable(new Uniform(-4.0, 4.0))
val xGaussianPrior = GaussianRV(0.0, 1.0)
val iidXPrior = IIDRandomVarDistr(xPrior) _

val (training, test, noiseLevel): (Int, Int, Double) = (100, 500, 0.05)

val likelihood = DataPipe((x: Double) => GaussianRV(math.atan(1000.0*x*x*x), noiseLevel))

val model = RejectionSamplingScheme(xPrior, likelihood)

val data: Stream[(DenseVector[Double], Double)] =
  (1 to training).map(_ => model.sample()).map(c => (DenseVector(c._1), c._2)).toStream

val testData = (1 to test).map(_ => model.sample()).map(c => (DenseVector(c._1), c._2)).toStream


val kernel = new RBFKernel(4.5)
val mlpkernel = new MLPKernel(40.0,4.0)

val noise = new DiracKernel(noiseLevel)
noise.blocked_hyper_parameters = noise.hyper_parameters

val startConf = mlpkernel.effective_state ++ noise.effective_state
val gpModel = new GPRegression(mlpkernel, noise, data)
gpModel.blockSize_(100)

val gs =
  new GridSearch(gpModel).setGridSize(3).setStepSize(0.2).setLogScale(false)

val (tunedGP, _) = gs.optimize(startConf)

tunedGP.persist()

val gpLikelihood = DataPipe((x: Double) => {
  val pD = tunedGP.predictiveDistribution(Seq(DenseVector(x)))
  GaussianRV(pD.mu.toBreezeVector(0), pD.covariance.toBreezeMatrix(0,0))
})

val gpProbModel = RejectionSamplingScheme(xPrior, gpLikelihood)

val gpTestSet = (1 to testData.length).map(_ => gpProbModel.sample()).toStream

scatter(testData.map(c => (c._1(0), c._2)))
hold()
scatter(data.map(c => (c._1(0), c._2)))
scatter(gpTestSet)
unhold()
title("Comparison of Noisy Data versus inferred function")
legend(List("Test Data samples", "Co-allocation/training data samples", "GP samples"))


val rvFTanH = MeasurableFunction(xPrior)((x: Double) => math.atan(1000.0*x*x*x))

//Histogram of test data
histogram(testData.map(_._2))
//Histogram generated by GP Probability Model
hold()
histogram((1 to testData.length).map(_ => rvFTanH.sample()))

histogram(gpTestSet.map(_._2))
unhold()
legend(List("Histogram of actual test data",
  "Histogram of noiseless function",
  "Histogram of generated data from GP probability model"))



// Example 2
val likelihood2 = DataPipe((x: Double) => GaussianRV(1/math.pow(2.0 + math.sin(3*math.Pi*x), 2.0), noiseLevel))

val model2 = RejectionSamplingScheme(xPrior, likelihood2)

val data2: Stream[(DenseVector[Double], Double)] =
  (1 to training).map(_ => model2.sample()).map(c => (DenseVector(c._1), c._2)).toStream

val testData2 = (1 to test).map(_ => model2.sample()).map(c => (DenseVector(c._1), c._2)).toStream

val perKernel = new PeriodicKernel(2.0, 1.5)
val noise = new DiracKernel(noiseLevel)
noise.blocked_hyper_parameters = noise.hyper_parameters

val startConf2 = perKernel.effective_state ++ noise.effective_state

val gpModel2 = new GPRegression(perKernel, noise, data2)

val gs2 =
  new CoupledSimulatedAnnealing(gpModel2).setGridSize(2).setStepSize(0.2).setLogScale(false)

val (tunedGP2, _) = gs2.optimize(startConf2)

tunedGP2.persist()

val gpLikelihood2 = DataPipe((x: Double) => {
  //val xStream = x.map(DenseVector(_))
  //tunedGP.predictiveDistribution(xStream)
  val pD = tunedGP2.predictiveDistribution(Seq(DenseVector(x)))
  GaussianRV(pD.mu.toBreezeVector(0), pD.covariance.toBreezeMatrix(0,0))
})

val gpProbModel2 = RejectionSamplingScheme(xPrior, gpLikelihood2)

val gpTestSet2 = (1 to testData2.length).map(_ => gpProbModel2.sample()).toStream
//scatter(gpTestSet2)

scatter(testData2.map(c => (c._1(0), c._2)))
hold()
scatter(data2.map(c => (c._1(0), c._2)))
scatter(gpTestSet2)
unhold()
title("Comparison of Noisy Data and inferred function for the case y ~ N(1/(2 + sin(3*pi*x))^2, 1E-10)")
legend(List("Test Data samples", "Co-allocation/training data samples", "GP samples"))

val f = DataPipe((x: Double) => 1/(2.0 + math.sin(3*math.Pi*x)))
val rvF = MeasurableFunction(xPrior)(DataPipe((x: Double) => 1/(2.0 + math.sin(3*math.Pi*x))))

//Histogram of test data
histogram(testData2.map(_._2))
//Histogram generated by GP Probability Model
hold()
histogram((1 to testData2.length).map(_ => rvF.sample()))

histogram(gpTestSet2.map(_._2))
unhold()
legend(List("Histogram of actual test data",
  "Histogram of noiseless function",
  "Histogram of generated data from GP probability model"))


// Multidimensional Example
/*
val (training, test, noiseLevel) = (500, 1000, 0.05)


val num_features: Int = 4
implicit val ev = VectorField(num_features)


val featuresPrior = RandomVariable(new MultivariateUniform(
  DenseVector.fill[Double](num_features)(-4.0),
  DenseVector.fill[Double](num_features)(4.0)))

val omega = featuresPrior.sample()

val likelihoodMult = DataPipe((x: DenseVector[Double]) =>
  GaussianRV(1/math.pow(2.0 + math.sin(omega dot x), 2.0), noiseLevel))

val model3 = RejectionSamplingScheme(featuresPrior, likelihoodMult)

val data3: Stream[(DenseVector[Double], Double)] =
  (1 to training).map(_ => model3.sample()).toStream

val testData3 = (1 to test).map(_ => model3.sample()).toStream

val f3 = DataPipe((x: DenseVector[Double]) => 1/math.pow(2.0 + math.sin(omega dot x), 2.0))
val rvF3 = MeasurableFunction(featuresPrior)(
  DataPipe((x: DenseVector[Double]) =>
    1/math.pow(2.0 + math.sin(omega dot x), 2.0))
)

val perKernel = new RBFKernel(4.0)//PeriodicKernel(4.0, 4.0)

val noise = new DiracKernel(noiseLevel)
noise.blocked_hyper_parameters = noise.hyper_parameters

val startConf3 = perKernel.effective_state ++ noise.effective_state

val gpModel3 = new GPRegression(perKernel, noise, data3)

val gs3 =
  new CoupledSimulatedAnnealing(gpModel3).setGridSize(2).setStepSize(0.2).setLogScale(false)

val (tunedGP3, _) = gs3.optimize(startConf3)

tunedGP3.persist()

val gpLikelihood3 = DataPipe((x: DenseVector[Double]) => {
  //val xStream = x.map(DenseVector(_))
  //tunedGP.predictiveDistribution(xStream)
  val pD = tunedGP3.predictiveDistribution(Seq(x))
  GaussianRV(pD.mu(0), pD.covariance(0,0))
})

val gpProbModel3 = RejectionSamplingScheme(featuresPrior, gpLikelihood3)

val gpTestSet3 = (1 to testData3.length).map(_ => gpProbModel3.sample()).toStream

//Histogram of test data
histogram(testData3.map(_._2))
//Histogram generated by GP Probability Model
hold()
histogram((1 to testData3.length).map(_ => rvF3.sample()))

histogram(gpTestSet3.map(_._2))
unhold()
legend(List("Histogram of actual test data",
  "Histogram of noiseless function",
  "Histogram of generated data from GP probability model"))
*/