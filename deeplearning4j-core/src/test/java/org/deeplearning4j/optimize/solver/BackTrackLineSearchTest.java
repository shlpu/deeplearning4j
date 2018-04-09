package org.deeplearning4j.optimize.solver;

import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.datasets.iterator.impl.IrisDataSetIterator;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.optimize.solvers.BackTrackLineSearch;
import org.deeplearning4j.optimize.stepfunctions.DefaultStepFunction;
import org.deeplearning4j.optimize.stepfunctions.NegativeDefaultStepFunction;
import org.junit.Before;
import org.junit.Test;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.workspace.LayerWorkspaceMgr;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Adam Gibson
 */
public class BackTrackLineSearchTest extends BaseDL4JTest {
    private DataSetIterator irisIter;
    private DataSet irisData;

    @Before
    public void before() {
        Nd4j.MAX_SLICES_TO_PRINT = -1;
        Nd4j.MAX_ELEMENTS_PER_SLICE = -1;
        Nd4j.ENFORCE_NUMERICAL_STABILITY = true;
        if (irisIter == null) {
            irisIter = new IrisDataSetIterator(5, 5);
        }
        if (irisData == null) {
            irisData = irisIter.next();
            irisData.normalizeZeroMeanZeroUnitVariance();
        }
    }



    @Test
    public void testSingleMinLineSearch() throws Exception {
        OutputLayer layer = getIrisLogisticLayerConfig(Activation.SOFTMAX, 100,
                        LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD);
        int nParams = layer.numParams();
        layer.setBackpropGradientsViewArray(Nd4j.create(1, nParams));
        layer.setInput(irisData.getFeatureMatrix(), LayerWorkspaceMgr.noWorkspaces());
        layer.setLabels(irisData.getLabels());
        layer.computeGradientAndScore(LayerWorkspaceMgr.noWorkspaces());

        BackTrackLineSearch lineSearch = new BackTrackLineSearch(layer, layer.getOptimizer());
        double step = lineSearch.optimize(layer.params(), layer.gradient().gradient(), layer.gradient().gradient());

        assertEquals(1.0, step, 1e-3);
    }

    @Test
    public void testSingleMaxLineSearch() throws Exception {
        double score1, score2;

        OutputLayer layer = getIrisLogisticLayerConfig(Activation.SOFTMAX, 100,
                        LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD);
        int nParams = layer.numParams();
        layer.setBackpropGradientsViewArray(Nd4j.create(1, nParams));
        layer.setInput(irisData.getFeatureMatrix(), LayerWorkspaceMgr.noWorkspaces());
        layer.setLabels(irisData.getLabels());
        layer.computeGradientAndScore(LayerWorkspaceMgr.noWorkspaces());
        score1 = layer.score();

        BackTrackLineSearch lineSearch =
                        new BackTrackLineSearch(layer, new NegativeDefaultStepFunction(), layer.getOptimizer());
        double step = lineSearch.optimize(layer.params(), layer.gradient().gradient(), layer.gradient().gradient());

        assertEquals(1.0, step, 1e-3);
    }


    @Test
    public void testMultMinLineSearch() throws Exception {
        double score1, score2;

        OutputLayer layer = getIrisLogisticLayerConfig(Activation.SOFTMAX, 100,
                        LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD);
        int nParams = layer.numParams();
        layer.setBackpropGradientsViewArray(Nd4j.create(1, nParams));
        layer.setInput(irisData.getFeatureMatrix(), LayerWorkspaceMgr.noWorkspaces());
        layer.setLabels(irisData.getLabels());
        layer.computeGradientAndScore(LayerWorkspaceMgr.noWorkspaces());
        score1 = layer.score();
        INDArray origGradient = layer.gradient().gradient().dup();

        NegativeDefaultStepFunction sf = new NegativeDefaultStepFunction();
        BackTrackLineSearch lineSearch = new BackTrackLineSearch(layer, sf, layer.getOptimizer());
        double step = lineSearch.optimize(layer.params(), layer.gradient().gradient(), layer.gradient().gradient());
        INDArray currParams = layer.params();
        sf.step(currParams, origGradient, step);
        layer.setParams(currParams);
        layer.computeGradientAndScore(LayerWorkspaceMgr.noWorkspaces());

        score2 = layer.score();

        assertTrue("score1=" + score1 + ", score2=" + score2, score1 > score2);

    }

    @Test
    public void testMultMaxLineSearch() throws Exception {
        double score1, score2;

        irisData.normalizeZeroMeanZeroUnitVariance();
        OutputLayer layer = getIrisLogisticLayerConfig(Activation.SOFTMAX, 100, LossFunctions.LossFunction.MCXENT);
        int nParams = layer.numParams();
        layer.setBackpropGradientsViewArray(Nd4j.create(1, nParams));
        layer.setInput(irisData.getFeatureMatrix(), LayerWorkspaceMgr.noWorkspaces());
        layer.setLabels(irisData.getLabels());
        layer.computeGradientAndScore(LayerWorkspaceMgr.noWorkspaces());
        score1 = layer.score();
        INDArray origGradient = layer.gradient().gradient().dup();

        DefaultStepFunction sf = new DefaultStepFunction();
        BackTrackLineSearch lineSearch = new BackTrackLineSearch(layer, sf, layer.getOptimizer());
        double step = lineSearch.optimize(layer.params().dup(), layer.gradient().gradient().dup(),
                        layer.gradient().gradient().dup());

        INDArray currParams = layer.params();
        sf.step(currParams, origGradient, step);
        layer.setParams(currParams);
        layer.computeGradientAndScore(LayerWorkspaceMgr.noWorkspaces());
        score2 = layer.score();

        assertTrue("score1 = " + score1 + ", score2 = " + score2, score1 < score2);
    }

    private static OutputLayer getIrisLogisticLayerConfig(Activation activationFunction, int maxIterations,
                    LossFunctions.LossFunction lossFunction) {
        NeuralNetConfiguration conf =
                        new NeuralNetConfiguration.Builder().seed(12345L).miniBatch(true)
                                        .maxNumLineSearchIterations(maxIterations)
                                        .layer(new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(lossFunction)
                                                        .nIn(4).nOut(3).activation(activationFunction)
                                                        .weightInit(WeightInit.XAVIER).build())
                                        .build();

        int numParams = conf.getLayer().initializer().numParams(conf);
        INDArray params = Nd4j.create(1, numParams);
        return (OutputLayer) conf.getLayer().instantiate(conf, null, 0, params, true);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testBackTrackLineGradientDescent() {
        OptimizationAlgorithm optimizer = OptimizationAlgorithm.LINE_GRADIENT_DESCENT;

        DataSetIterator irisIter = new IrisDataSetIterator(1, 1);
        DataSet data = irisIter.next();

        MultiLayerNetwork network = new MultiLayerNetwork(getIrisMultiLayerConfig(Activation.SIGMOID, optimizer));
        network.init();
        IterationListener listener = new ScoreIterationListener(1);
        network.setListeners(Collections.singletonList(listener));
        double oldScore = network.score(data);
        for( int i=0; i<100; i++ ) {
            network.fit(data.getFeatureMatrix(), data.getLabels());
        }
        double score = network.score();
        assertTrue(score < oldScore);
    }

    @Test
    public void testBackTrackLineCG() {
        OptimizationAlgorithm optimizer = OptimizationAlgorithm.CONJUGATE_GRADIENT;

        DataSet data = irisIter.next();
        data.normalizeZeroMeanZeroUnitVariance();
        MultiLayerNetwork network = new MultiLayerNetwork(getIrisMultiLayerConfig(Activation.RELU, optimizer));
        network.init();
        IterationListener listener = new ScoreIterationListener(1);
        network.setListeners(Collections.singletonList(listener));
        double firstScore = network.score(data);

        for( int i=0; i<5; i++ ) {
            network.fit(data.getFeatureMatrix(), data.getLabels());
        }
        double score = network.score();
        assertTrue(score < firstScore);

    }

    @Test
    public void testBackTrackLineLBFGS() {
        OptimizationAlgorithm optimizer = OptimizationAlgorithm.LBFGS;
        DataSet data = irisIter.next();
        data.normalizeZeroMeanZeroUnitVariance();
        MultiLayerNetwork network = new MultiLayerNetwork(getIrisMultiLayerConfig(Activation.RELU, optimizer));
        network.init();
        IterationListener listener = new ScoreIterationListener(1);
        network.setListeners(Collections.singletonList(listener));
        double oldScore = network.score(data);

        for( int i=0; i<5; i++ ) {
            network.fit(data.getFeatureMatrix(), data.getLabels());
        }
        double score = network.score();
        assertTrue(score < oldScore);

    }

    @Test(expected = Exception.class)
    public void testBackTrackLineHessian() {
        OptimizationAlgorithm optimizer = OptimizationAlgorithm.HESSIAN_FREE;
        DataSet data = irisIter.next();

        MultiLayerNetwork network = new MultiLayerNetwork(getIrisMultiLayerConfig(Activation.RELU, optimizer));
        network.init();
        IterationListener listener = new ScoreIterationListener(1);
        network.setListeners(Collections.singletonList(listener));

        for( int i=0; i<100; i++ ) {
            network.fit(data.getFeatureMatrix(), data.getLabels());
        }
    }



    private static MultiLayerConfiguration getIrisMultiLayerConfig(Activation activationFunction, OptimizationAlgorithm optimizer) {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().optimizationAlgo(optimizer)
                        .miniBatch(false).updater(new Nesterovs(0.9)).seed(12345L).list()
                        .layer(0, new DenseLayer.Builder().nIn(4).nOut(100).weightInit(WeightInit.XAVIER)
                                        .activation(activationFunction).build())
                        .layer(1, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(
                                        LossFunctions.LossFunction.MCXENT).nIn(100).nOut(3)
                                                        .weightInit(WeightInit.XAVIER).activation(Activation.SOFTMAX)
                                                        .build())
                        .backprop(true).build();


        return conf;
    }

}
