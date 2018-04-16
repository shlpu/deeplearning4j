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

package org.deeplearning4j.nn.layers.convolution;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.exception.DL4JInvalidInputException;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.CacheMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.AbstractLayer;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.CustomOp;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.impl.layers.convolution.Upsampling;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.linalg.workspace.LayerWorkspaceMgr;

import java.util.Arrays;


/**
 * Space to batch utility layer for convolutional input types.
 * <p>
 * Does a 2-dimensional space to batch operation, i.e. ransforms data from a tensor from 2 spatial dimensions into batch dimension
 * according to the "blocks" specified (a vector of length 2). Afterwards the spatial dimensions are optionally padded,
 * as specified in "padding", a tensor of dim (2, 2), denoting the padding range.
 * <p>
 * Example:
 * input:         [[[[1], [2]], [[3], [4]]]]
 * input shape:   [1, 2, 2, 1]
 * blocks:        [2, 2]
 * padding:       [[0, 0], [0, 0]]
 * <p>
 * output:        [[[[1]]], [[[2]]], [[[3]]], [[[4]]]]
 * output shape:  [4, 1, 1, 1]
 *
 * @author Max Pumperla
 */
@Slf4j
public class SpaceToBatch extends AbstractLayer<org.deeplearning4j.nn.conf.layers.SpaceToBatchLayer> {

    public SpaceToBatch(NeuralNetConfiguration conf) {
        super(conf);
    }

    public SpaceToBatch(NeuralNetConfiguration conf, INDArray input) {
        super(conf, input);
    }

    private int[] getBlocks() {
        return layerConf().getBlocks();
    }

    private int[][] getPadding() {
        return layerConf().getPadding();
    }

    private INDArray getBlocksArray() {
        int[] intBlocks = layerConf().getBlocks();
        return Nd4j.create(new double[] {intBlocks[0], intBlocks[1]});
    }

    private INDArray getPaddingArray() {
        int[][] intPad = layerConf().getPadding();
        return Nd4j.create( new double[][] { {intPad[0][0], intPad[0][1]}, {intPad[1][0], intPad[1][1]}});
    }


    @Override
    public Type type() {
        return Type.CONVOLUTIONAL;
    }


    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon) {

        int miniBatch = input.size(0);
        int inDepth = input.size(1);
        int inH = input.size(2);
        int inW = input.size(3);

        INDArray outEpsilon = Nd4j.createUninitialized(miniBatch * inDepth * inH * inW);
        INDArray reshapedEpsilon = outEpsilon.reshape('c', miniBatch, inDepth, inH, inW);

        Gradient gradient = new DefaultGradient();

        CustomOp op = DynamicCustomOp.builder("batch_to_space")
                .addInputs(epsilon, getBlocksArray(), getPaddingArray())
                .addOutputs(reshapedEpsilon)
                .callInplace(false)
                .build();
        Nd4j.getExecutioner().exec(op);

        return new Pair<>(gradient, reshapedEpsilon);
    }


    @Override
    public INDArray preOutput(boolean training) {
        return preOutput(training, false);
    }

    public INDArray preOutput(boolean training, boolean forBackprop) {
        applyDropOutIfNecessary(training, null);

        if (input.rank() != 4) {
            throw new DL4JInvalidInputException("Got rank " + input.rank()
                    + " array as input to space to batch with shape " + Arrays.toString(input.shape())
                    + ". Expected rank 4 array with shape [minibatchSize, depth, inputHeight, inputWidth]. "
                    + layerId());
        }

        if (preOutput != null && forBackprop) {
            return preOutput;
        }

        int inMiniBatch = input.size(0);
        int depth = input.size(1);
        int inH = input.size(2);
        int inW = input.size(3);

        int[] blocks = getBlocks();
        int[][] padding = getPadding();

        int paddedH = inH + padding[0][0] + padding[0][1];
        int paddedW = inW + padding[1][0] + padding[1][1];

        int outH = paddedH / blocks[0];
        int outW = paddedW / blocks[1];
        int outMiniBatch = inMiniBatch * blocks[0] * blocks[1];

        INDArray out = Nd4j.create(outMiniBatch * depth * outH * outW);
        INDArray reshapedOut = out.reshape('c', outMiniBatch, depth, outH, outW);

        CustomOp op = DynamicCustomOp.builder("space_to_batch")
                .addInputs(input, getBlocksArray(), getPaddingArray())
                .addOutputs(reshapedOut)
                .build();
        Nd4j.getExecutioner().exec(op);

        return reshapedOut;
    }

    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        applyDropOutIfNecessary(training, workspaceMgr);
        return preOutput(training);
    }


    @Override
    public double calcL2(boolean backpropParamsOnly) {
        return 0;
    }

    @Override
    public double calcL1(boolean backpropParamsOnly) {
        return 0;
    }

    @Override
    public Layer transpose() {
        throw new UnsupportedOperationException(layerId());
    }

    @Override
    public Layer clone() {
        return new SpaceToBatch(conf.clone());
    }

    @Override
    public boolean isPretrainLayer() {
        return false;
    }

    @Override
    public void clearNoiseWeightParams() {
        //No op
    }

    @Override
    public void iterate(INDArray input) {
        throw new UnsupportedOperationException(layerId());
    }

    @Override
    public Gradient gradient() {
        throw new UnsupportedOperationException("Not supported - no parameters");
    }

    @Override
    public void fit() {

    }

    @Override
    public int numParams() {
        return 0;
    }

    @Override
    public void fit(INDArray input) {
    }

    @Override
    public void computeGradientAndScore() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public double score() {
        return 0;
    }

    @Override
    public void accumulateScore(double accum) {
        throw new UnsupportedOperationException(layerId());
    }


    @Override
    public void update(INDArray gradient, String paramType) {

    }

    @Override
    public INDArray params() {
        return null;
    }

    @Override
    public INDArray getParam(String param) {
        return params();
    }

    @Override
    public void setParams(INDArray params) {
    }

}
