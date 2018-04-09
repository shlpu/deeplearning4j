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

package org.deeplearning4j.spark.impl.layer;

import org.apache.spark.broadcast.Broadcast;
import org.datavec.spark.functions.FlatMapFunctionAdapter;
import org.datavec.spark.transform.BaseFlatMapFunctionAdaptee;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.layers.OutputLayer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.workspace.LayerWorkspaceMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Iterative reduce with
 * flat map using map partitions
 *
 * @author Adam Gibson
 */
public class IterativeReduceFlatMap extends BaseFlatMapFunctionAdaptee<Iterator<DataSet>, INDArray> {

    public IterativeReduceFlatMap(String json, Broadcast<INDArray> params) {
        super(new IterativeReduceFlatMapAdapter(json, params));
    }
}


/**
 * Iterative reduce with
 * flat map using map partitions
 *
 * @author Adam Gibson
 */
class IterativeReduceFlatMapAdapter implements FlatMapFunctionAdapter<Iterator<DataSet>, INDArray> {

    private String json;
    private Broadcast<INDArray> params;
    private static Logger log = LoggerFactory.getLogger(IterativeReduceFlatMap.class);

    /**
     * Pass in json configuration and baseline parameters
     * @param json json configuration for the network
     * @param params the parameters to use for the network
     */
    public IterativeReduceFlatMapAdapter(String json, Broadcast<INDArray> params) {
        this.json = json;
        this.params = params;
    }



    @Override
    public Iterable<INDArray> call(Iterator<DataSet> dataSetIterator) throws Exception {
        if (!dataSetIterator.hasNext()) {
            return Collections.singletonList(Nd4j.zeros(params.value().shape()));
        }

        List<DataSet> collect = new ArrayList<>();
        while (dataSetIterator.hasNext()) {
            collect.add(dataSetIterator.next());
        }

        DataSet data = DataSet.merge(collect);
        log.debug("Training on " + data.labelCounts());
        NeuralNetConfiguration conf = NeuralNetConfiguration.fromJson(json);
        int numParams = conf.getLayer().initializer().numParams(conf);
        INDArray thisParams = Nd4j.create(1, numParams);
        Layer network = conf.getLayer().instantiate(conf, null, 0, thisParams, true);
        network.setBackpropGradientsViewArray(Nd4j.create(1, numParams));
        INDArray val = params.value().unsafeDuplication();
        if (val.length() != network.numParams())
            throw new IllegalStateException(
                            "Network did not have same number of parameters as the broadcast set parameters");
        network.setParams(val);
        if (network instanceof OutputLayer) {
            OutputLayer o = (OutputLayer) network;
            o.fit(data);
        } else
            network.fit(data.getFeatureMatrix(), LayerWorkspaceMgr.noWorkspaces());

        return Collections.singletonList(network.params());

    }
}
