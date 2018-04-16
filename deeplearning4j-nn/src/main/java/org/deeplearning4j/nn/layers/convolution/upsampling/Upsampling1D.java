/*-
 *
 *  * Copyright 2015 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package org.deeplearning4j.nn.layers.convolution.upsampling;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.exception.DL4JInvalidInputException;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.BaseUpsamplingLayer;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.CustomOp;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.linalg.workspace.LayerWorkspaceMgr;

import java.util.Arrays;


/**
 * 1D Upsampling layer.
 * <p>
 * Used for upsampling a 1D convolution. Currently derived from 2D version.
 * For forward and backward pass we add a dummy dimension, apply the 2D version
 * and strip the extra dimension again. Eventually, we will want to migrate to a
 * proper 1D version without this overhead.
 *
 * @author Max Pumperla
 */
@Slf4j
public class Upsampling1D extends Upsampling2D {


    public Upsampling1D(NeuralNetConfiguration conf) {
        super(conf);
    }

    public Upsampling1D(NeuralNetConfiguration conf, INDArray input) {
        super(conf, input);
    }


    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {

        int size = ((BaseUpsamplingLayer) layerConf()).getSize();
        epsilon = epsilon.reshape(epsilon.size(0), epsilon.size(1), epsilon.size(2), 1);
        // we replicate the error term times "size" so that backprop works properly on it
        epsilon = epsilon.repeat(3, size);

        INDArray originalInput = input;
        input = input.reshape(input.size(0), input.size(1), input.size(2), 1);

        int miniBatch = input.size(0);
        int inDepth = input.size(1);
        int inH = input.size(2);
        int inW = input.size(3);


        INDArray outEpsilon = Nd4j.create(miniBatch * inDepth * inH * inW);
        INDArray reshapedEpsilon = outEpsilon.reshape('c', miniBatch, inDepth, inH, inW);

        INDArray forwardOutput  = preOutput(true, true, LayerWorkspaceMgr.noWorkspaces());
        forwardOutput = forwardOutput.reshape(
                forwardOutput.size(0), forwardOutput.size(1), forwardOutput.size(2), 1);
        forwardOutput = forwardOutput.repeat(3, size);

        CustomOp op = DynamicCustomOp.builder("upsampling_bp")
                .addIntegerArguments(size)
                .addInputs(forwardOutput, epsilon)
                .addOutputs(reshapedEpsilon)
                .callInplace(false)
                .build();
        Nd4j.getExecutioner().exec(op);

        Gradient gradient = new DefaultGradient();

        reshapedEpsilon = reshapedEpsilon.slice(0, 3);
        input = originalInput;

        // Since we aggregate the gradient across "size" slices, we need to normalize afterwards.
        return new Pair<>(gradient, reshapedEpsilon.divi(size));
    }

    @Override
    protected int getSize(){
        return ((org.deeplearning4j.nn.conf.layers.Upsampling1D)conf.getLayer()).getSize();
    }

    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        if (input.rank() != 3)
            throw new DL4JInvalidInputException("Got rank " + input.rank()
                    + " array as input to Subsampling1DLayer with shape " + Arrays.toString(input.shape())
                    + ". Expected rank 3 array with shape [minibatchSize, features, length]. " + layerId());

        // add singleton fourth dimension to input
        INDArray origInput = input;
        input = input.reshape(input.size(0), input.size(1), input.size(2), 1);

        // call 2D SubsamplingLayer's activate method
        INDArray acts = super.activate(training, workspaceMgr);

        // remove singleton fourth dimension from input and output activations
        input = origInput;
        acts = acts.reshape(acts.size(0), acts.size(1), acts.size(2));

        return acts;
    }

    @Override
    protected INDArray preOutput(boolean training, boolean forBackprop, LayerWorkspaceMgr workspaceMgr) {
        INDArray originalInput = input;
        input = input.reshape(input.size(0), input.size(1), input.size(2), 1);

        INDArray preOutput = super.preOutput(training, forBackprop, workspaceMgr);

        input = originalInput;
        preOutput = preOutput.slice(0, 3);

        return preOutput;
    }


}
