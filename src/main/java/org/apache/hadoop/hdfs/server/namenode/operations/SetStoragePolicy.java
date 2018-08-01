/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hadoop.hdfs.server.namenode.operations;

import java.util.Collection;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.server.namenode.NNAConstants;

public class SetStoragePolicy extends BaseOperation {

  private final String newStoragePolicy;

  public SetStoragePolicy(
      Collection<INode> toSetRep,
      String query,
      String owner,
      String logBaseDir,
      FileSystem fs,
      String newStoragePolicy) {
    super(toSetRep, owner, query, logBaseDir, fs);
    this.newStoragePolicy = newStoragePolicy;
  }

  @Override
  public synchronized boolean performOp() {
    if (!hasNext()) {
      return false;
    }
    String path = nextToOperate.getFullPathName();
    LOG.info("About to setStoragePolicy: {}", path);
    boolean file = nextToOperate.isFile();
    boolean dir = nextToOperate.isDirectory();
    boolean success = true;
    String inodeType;
    /*
     * TODO:: SETSTORAGEPOLICY WILL LOOK LIKE THIS:
     * try {
     *   ((DistributedFileSystem) fs).setStoragePolicy(new Path(path), newStoragePolicy);
     * } catch (IOException e) {
     *   success = false;
     * }
     */
    if (file) {
      /* TODO: Insert actual setStoragePolicy code here. */
      LOG.info("SetStoragePolicy'd file.");
      inodeType = "FILE";
    } else if (dir) {
      /* TODO: Insert actual setStoragePolicy code here. */
      LOG.info("SetStoragePolicy'd dir.");
      inodeType = "DIR";
    } else {
      LOG.info("Could not determine INode type. Did not setStoragePolicy.");
      return false;
    }
    synchronized (pathsOperated) {
      synchronized (toOperate) {
        log.logOp(path, inodeType, success);
        pathsOperated.add(path);
        iterator.remove();
      }
    }
    nextToOperate = iterator.hasNext() ? iterator.next() : null;
    return true;
  }

  @Override
  public String type() {
    return NNAConstants.OPERATION.setStoragePolicy.name();
  }
}
