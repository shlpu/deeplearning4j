package org.deeplearning4j.zoo.model;

import lombok.NoArgsConstructor;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration.GraphBuilder;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.conf.layers.objdetect.Yolo2OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.zoo.ModelMetaData;
import org.deeplearning4j.zoo.PretrainedType;
import org.deeplearning4j.zoo.ZooModel;
import org.deeplearning4j.zoo.ZooType;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;

import static org.deeplearning4j.zoo.model.helper.DarknetHelper.*;

/**
 * YOLOv2
 *  Reference: https://arxiv.org/pdf/1612.08242.pdf
 *
 * <p>ImageNet+COCO weights for this model are available and have been converted from https://pjreddie.com/darknet/yolo/
 * using https://github.com/allanzelener/YAD2K and the following code.</p>
 *
 * <pre>{@code
 *     String filename = "yolo.h5";
 *     KerasLayer.registerCustomLayer("Lambda", KerasSpaceToDepth.class);
 *     ComputationGraph graph = KerasModelImport.importKerasModelAndWeights(filename, false);
 *     INDArray priors = Nd4j.create(priorBoxes);
 *
 *     FineTuneConfiguration fineTuneConf = new FineTuneConfiguration.Builder()
 *             .seed(seed)
 *             .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
 *             .gradientNormalization(GradientNormalization.RenormalizeL2PerLayer)
 *             .gradientNormalizationThreshold(1.0)
 *             .updater(new Adam.Builder().learningRate(1e-3).build())
 *             .l2(0.00001)
 *             .activation(Activation.IDENTITY)
 *             .trainingWorkspaceMode(workspaceMode)
 *             .inferenceWorkspaceMode(workspaceMode)
 *             .build();
 *
 *     ComputationGraph model = new TransferLearning.GraphBuilder(graph)
 *             .fineTuneConfiguration(fineTuneConf)
 *             .addLayer("outputs",
 *                     new Yolo2OutputLayer.Builder()
 *                             .boundingBoxPriors(priors)
 *                             .build(),
 *                     "conv2d_23")
 *             .setOutputs("outputs")
 *             .build();
 *
 *     System.out.println(model.summary(InputType.convolutional(608, 608, 3)));
 *
 *     ModelSerializer.writeModel(model, "yolo2_dl4j_inference.v1.zip", false);
 *}</pre>
 *
 * The channels of the 608x608 input images need to be in RGB order (not BGR), with values normalized within [0, 1].
 *
 * @author saudet
 */
@NoArgsConstructor
public class YOLO2 extends ZooModel {

    public static int nBoxes = 5;
    public static double[][] priorBoxes = {{0.57273, 0.677385}, {1.87446, 2.06253}, {3.33843, 5.47434}, {7.88282, 3.52778}, {9.77052, 9.16828}};

    private int[] inputShape = {3, 608, 608};
    private int numLabels;
    private long seed;
    private WorkspaceMode workspaceMode;
    private ConvolutionLayer.AlgoMode cudnnAlgoMode;

    public YOLO2(int numLabels, long seed) {
        this(numLabels, seed, WorkspaceMode.ENABLED);
    }

    public YOLO2(int numLabels, long seed, WorkspaceMode workspaceMode) {
        this.numLabels = numLabels;
        this.seed = seed;
        this.workspaceMode = workspaceMode;
        this.cudnnAlgoMode = workspaceMode == WorkspaceMode.ENABLED ? ConvolutionLayer.AlgoMode.PREFER_FASTEST
                        : ConvolutionLayer.AlgoMode.NO_WORKSPACE;
    }

    @Override
    public String pretrainedUrl(PretrainedType pretrainedType) {
        if (pretrainedType == PretrainedType.IMAGENET)
            return "http://blob.deeplearning4j.org/models/yolo2_dl4j_inference.v1.zip";
        else
            return null;
    }

    @Override
    public long pretrainedChecksum(PretrainedType pretrainedType) {
        if (pretrainedType == PretrainedType.IMAGENET)
            return 1357637732L;
        else
            return 0L;
    }

    @Override
    public ZooType zooType() {
        return ZooType.YOLO2;
    }

    @Override
    public Class<? extends Model> modelType() {
        return ComputationGraph.class;
    }

    public ComputationGraphConfiguration conf() {
        INDArray priors = Nd4j.create(priorBoxes);

        GraphBuilder graphBuilder = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .gradientNormalization(GradientNormalization.RenormalizeL2PerLayer)
                .gradientNormalizationThreshold(1.0)
                .updater(new Adam.Builder().learningRate(1e-3).build())
                .l2(0.00001)
                .activation(Activation.IDENTITY)
                .trainingWorkspaceMode(workspaceMode)
                .inferenceWorkspaceMode(workspaceMode)
                .cudnnAlgoMode(cudnnAlgoMode)
                .graphBuilder()
                .addInputs("input")
                .setInputTypes(InputType.convolutional(inputShape[2], inputShape[1], inputShape[0]));

        addLayers(graphBuilder, 1, 3, inputShape[0],  32, 2);

        addLayers(graphBuilder, 2, 3, 32, 64, 2);

        addLayers(graphBuilder, 3, 3, 64, 128, 0);
        addLayers(graphBuilder, 4, 1, 128, 64, 0);
        addLayers(graphBuilder, 5, 3, 64, 128, 2);

        addLayers(graphBuilder, 6, 3, 128, 256, 0);
        addLayers(graphBuilder, 7, 1, 256, 128, 0);
        addLayers(graphBuilder, 8, 3, 128, 256, 2);

        addLayers(graphBuilder, 9, 3, 256, 512, 0);
        addLayers(graphBuilder, 10, 1, 512, 256, 0);
        addLayers(graphBuilder, 11, 3, 256, 512, 0);
        addLayers(graphBuilder, 12, 1, 512, 256, 0);
        addLayers(graphBuilder, 13, 3, 256, 512, 2);

        addLayers(graphBuilder, 14, 3, 512, 1024, 0);
        addLayers(graphBuilder, 15, 1, 1024, 512, 0);
        addLayers(graphBuilder, 16, 3, 512, 1024, 0);
        addLayers(graphBuilder, 17, 1, 1024, 512, 0);
        addLayers(graphBuilder, 18, 3, 512, 1024, 0);

        // #######

        addLayers(graphBuilder, 19, 3, 1024, 1024, 0);
        addLayers(graphBuilder, 20, 3, 1024, 1024, 0);

        // route
        addLayers(graphBuilder, 21, "activation_13", 1, 512, 64, 0, 0);

        // reorg
        graphBuilder.addLayer("rearrange_21",new SpaceToDepthLayer.Builder(2).build(), "activation_21")
        // route
                .addVertex("concatenate_21", new MergeVertex(),
                        "rearrange_21", "activation_20");

        addLayers(graphBuilder, 22, "concatenate_21", 3, 1024 + 256, 1024, 0, 0);

        graphBuilder
                .addLayer("convolution2d_23",
                        new ConvolutionLayer.Builder(1,1)
                                .nIn(1024)
                                .nOut(nBoxes * (5 + numLabels))
                                .weightInit(WeightInit.XAVIER)
                                .stride(1,1)
                                .convolutionMode(ConvolutionMode.Same)
                                .weightInit(WeightInit.RELU)
                                .activation(Activation.IDENTITY)
                                .build(),
                        "activation_22")
                .addLayer("outputs",
                        new Yolo2OutputLayer.Builder()
                                .boundingBoxPriors(priors)
                                .build(),
                        "convolution2d_23")
                .setOutputs("outputs").backprop(true).pretrain(false);

        return graphBuilder.build();
    }

    @Override
    public ComputationGraph init() {
        ComputationGraph model = new ComputationGraph(conf());
        model.init();

        return model;
    }

    @Override
    public ModelMetaData metaData() {
        return new ModelMetaData(new int[][] {inputShape}, 1, ZooType.CNN);
    }

    @Override
    public void setInputShape(int[][] inputShape) {
        this.inputShape = inputShape[0];
    }
}
