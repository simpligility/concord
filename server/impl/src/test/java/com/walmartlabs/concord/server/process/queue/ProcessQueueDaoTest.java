package com.walmartlabs.concord.server.process.queue;

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessKind;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

@Ignore("requires a local DB instance")
public class ProcessQueueDaoTest extends AbstractDaoTest {

    private ProcessQueueDao queueDao;

    @Before
    public void setUp() throws Exception {
        queueDao = new ProcessQueueDao(getConfiguration());
    }

    @Test
    public void test() throws Exception {
        UUID instanceA = UUID.randomUUID();
        queueDao.insertInitial(instanceA, ProcessKind.DEFAULT, null, "testProject", "testInitiator");
        queueDao.update(instanceA, ProcessStatus.ENQUEUED);

        // add a small delay between two jobs
        Thread.sleep(100);

        UUID instanceB = UUID.randomUUID();
        queueDao.insertInitial(instanceB, ProcessKind.DEFAULT, null, "testProject", "testInitiator");
        queueDao.update(instanceB, ProcessStatus.ENQUEUED);

        // ---

        ProcessEntry e1 = queueDao.poll();
        ProcessEntry e2 = queueDao.poll();
        ProcessEntry e3 = queueDao.poll();

        assertNotNull(e1);
        assertEquals(instanceA, e1.getInstanceId());

        assertNotNull(e2);
        assertEquals(instanceB, e2.getInstanceId());

        assertNull(e3);
    }
}
