package org.deeplearning4j.dbn;

import org.apache.commons.math3.random.RandomGenerator;
import org.deeplearning4j.nn.BaseMultiLayerNetwork;
import org.deeplearning4j.nn.NeuralNetwork;
import org.deeplearning4j.rbm.CRBM;
import org.deeplearning4j.rbm.RBM;
import org.jblas.DoubleMatrix;

/**
 * Continuous Deep Belief Network.
 * 
 * Uses a continuous RBM in the first layer
 * @author Adam Gibson
 *
 */
public class CDBN extends DBN {

	private static final long serialVersionUID = 3838174630098935941L;

	
	public CDBN() {}
	
	public CDBN(int nIn, int[] hiddenLayerSizes, int nOuts, int nLayers,
			RandomGenerator rng, DoubleMatrix input,DoubleMatrix labels) {
		super(nIn, hiddenLayerSizes, nOuts, nLayers, rng, input,labels);
	}

	public CDBN(int nIns, int[] hiddenLayerSizes, int nOuts, int nLayers,
			RandomGenerator rng) {
		super(nIns, hiddenLayerSizes, nOuts, nLayers, rng);
	}

	
	
	@Override
	public NeuralNetwork createLayer(DoubleMatrix input, int nVisible,
			int nHidden, DoubleMatrix W, DoubleMatrix hBias,
			DoubleMatrix vBias, RandomGenerator rng,int index) {
		if(index == 0)
			return new CRBM.Builder().useRegularization(isUseRegularization())
					.withDistribution(getDist()).useAdaGrad(isUseAdaGrad())
		.withHBias(hBias).numberOfVisible(nVisible).numHidden(nHidden).withSparsity(getSparsity())
		.withInput(input).withL2(getL2()).fanIn(getFanIn()).renderWeights(getRenderWeightsEveryNEpochs())
		.withRandom(rng).withWeights(W).build();
		else
			return new RBM.Builder().useAdaGrad(isUseAdaGrad())
		.useRegularization(isUseRegularization()).withDistribution(getDist())
		.withHBias(hBias).numberOfVisible(nVisible).numHidden(nHidden).withSparsity(getSparsity())
		.withInput(input).withL2(getL2()).fanIn(getFanIn()).renderWeights(getRenderWeightsEveryNEpochs())
		.withRandom(rng).withWeights(W).build();
	}

	@Override
	public NeuralNetwork[] createNetworkLayers(int numLayers) {
		return new RBM[numLayers];
	}
	
	public static class Builder extends BaseMultiLayerNetwork.Builder<CDBN> {
		public Builder() {
			this.clazz = CDBN.class;
		}
	}
	

}
