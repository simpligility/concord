package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.cfg.ProcessStateConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Named
public class StateImportingProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(StateImportingProcessor.class);

    private final ProcessStateManager stateManager;
    private final LogManager logManager;
    private final ProcessStateConfiguration cfg;

    @Inject
    public StateImportingProcessor(ProcessStateManager stateManager, LogManager logManager,
                                   ProcessStateConfiguration cfg) {

        this.stateManager = stateManager;
        this.logManager = logManager;
        this.cfg = cfg;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        stateManager.replacePath(instanceId, workspace, (path -> filter(instanceId, workspace, path)));

        return chain.process(payload);
    }

    private boolean filter(UUID instanceId, Path baseDir, Path p) {
        if (p.isAbsolute()) {
            log.warn("filter ['{}'] -> can't filter absolute paths", p);
            return true;
        }

        String n = p.toString();
        for (String i : InternalConstants.Files.IGNORED_FILES) {
            if (n.matches(i)) {
                return false;
            }
        }

        try {
            Path f = baseDir.resolve(p);
            if (Files.isRegularFile(f) && Files.size(f) >= cfg.getMaxFileSize()) {
                logManager.warn(instanceId, "{} is larger than the allowed limit, skipping...", p);
                return false;
            }
        } catch (IOException e) {
            logManager.error(instanceId, "Error while importing a process file: {}", p, e);
        }

        return true;
    }
}
