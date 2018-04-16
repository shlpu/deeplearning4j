package org.deeplearning4j.earlystopping.scorecalc;

import org.deeplearning4j.earlystopping.scorecalc.base.BaseScoreCalculator;
import org.deeplearning4j.eval.RegressionEvaluation;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.feedforward.autoencoder.AutoEncoder;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.workspace.LayerWorkspaceMgr;

/**
 * Score function for a MultiLayerNetwork or ComputationGraph with a single
 * {@link org.deeplearning4j.nn.conf.layers.AutoEncoder} layer.
 * Calculates the specified {@link RegressionEvaluation.Metric} on the layer's reconstructions.
 *
 * @author Alex Black
 */
public class AutoencoderScoreCalculator extends BaseScoreCalculator<Model> {

    protected final RegressionEvaluation.Metric metric;
    protected RegressionEvaluation evaluation;

    public AutoencoderScoreCalculator(RegressionEvaluation.Metric metric, DataSetIterator iterator){
        super(iterator);
        this.metric = metric;
    }

    @Override
    protected void reset() {
        evaluation = new RegressionEvaluation();
    }

    @Override
    protected INDArray output(Model net, INDArray input, INDArray fMask, INDArray lMask) {

        Layer l;
        if(net instanceof MultiLayerNetwork) {
            MultiLayerNetwork network = (MultiLayerNetwork)net;
            l = network.getLayer(0);
        } else {
            ComputationGraph network = (ComputationGraph)net;
            l = network.getLayer(0);
        }

        if (!(l instanceof AutoEncoder)) {
            throw new UnsupportedOperationException("Can only score networks with autoencoder layers as first layer -" +
                    " got " + l.getClass().getSimpleName());
        }
        AutoEncoder ae = (AutoEncoder) l;

        INDArray encode = ae.encode(input, false, LayerWorkspaceMgr.noWorkspaces());
        return ae.decode(encode);
    }

    @Override
    protected INDArray[] output(Model network, INDArray[] input, INDArray[] fMask, INDArray[] lMask) {
        return new INDArray[]{output(network, get0(input), get0(fMask), get0(lMask))};
    }

    @Override
    protected double scoreMinibatch(Model network, INDArray features, INDArray labels, INDArray fMask,
                                    INDArray lMask, INDArray output) {
        evaluation.eval(features, output);
        return 0.0; //Not used
    }

    @Override
    protected double scoreMinibatch(Model network, INDArray[] features, INDArray[] labels, INDArray[] fMask, INDArray[] lMask, INDArray[] output) {
        return scoreMinibatch(network, get0(features), get0(labels), get0(fMask), get0(lMask), get0(output));
    }

    @Override
    protected double finalScore(double scoreSum, int minibatchCount, int exampleCount) {
        return evaluation.scoreForMetric(metric);
    }
}
