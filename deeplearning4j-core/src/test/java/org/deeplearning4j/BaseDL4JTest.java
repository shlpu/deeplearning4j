package org.deeplearning4j;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.ops.executioner.OpExecutioner;
import org.nd4j.linalg.factory.Nd4j;

import static org.junit.Assert.assertNull;

@Slf4j
public class BaseDL4JTest {

    public OpExecutioner.ProfilingMode getProfilingMode(){
        return OpExecutioner.ProfilingMode.SCOPE_PANIC;
    }

    @Before
    public void beforeTest(){
        Nd4j.getExecutioner().setProfilingMode(getProfilingMode());
    }

    @After
    public void afterTest(){
        //Attempt to keep workspaces isolated between tests
        Nd4j.getWorkspaceManager().destroyAllWorkspacesForCurrentThread();
        MemoryWorkspace currWS = Nd4j.getMemoryManager().getCurrentWorkspace();
        Nd4j.getMemoryManager().setCurrentWorkspace(null);
        if(currWS != null){
            //Not really safe to continue testing under this situation... other tests will likely fail with obscure
            // errors that are hard to track back to this
            log.error("Open workspace leaked from test! Exiting - {}, isOpen = {} - {}", currWS.getId(), currWS.isScopeActive(), currWS);
            System.exit(1);
        }
    }

}
