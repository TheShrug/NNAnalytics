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

package com.paypal.nnanalytics;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import com.paypal.namenode.WebServerMain;
import com.paypal.security.SecurityConfiguration;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.server.namenode.GSetGenerator;
import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.server.namenode.INodeWithAdditionalFields;
import org.apache.hadoop.hdfs.server.namenode.queries.Transforms;
import org.apache.hadoop.util.GSet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestTransforms {

  private static WebServerMain nna;

  @BeforeClass
  public static void beforeClass() throws Exception {
    GSetGenerator gSetGenerator = new GSetGenerator();
    gSetGenerator.clear();
    GSet<INode, INodeWithAdditionalFields> gset = gSetGenerator.getGSet((short) 3, 10, 500);
    nna = new WebServerMain();
    SecurityConfiguration conf = new SecurityConfiguration();
    conf.set("ldap.enable", "false");
    conf.set("authorization.enable", "false");
    conf.set("nna.historical", "false");
    conf.set("nna.base.dir", MiniDFSCluster.getBaseDirectory());
    nna.init(conf, gset);
  }

  @AfterClass
  public static void tearDown() {
    if (nna != null) {
      nna.shutdown();
    }
  }

  @Test
  public void testTransformReplicationFactor() {
    Map<String, Function<INode, Long>> transformMap =
        Transforms.getAttributeTransforms("fileReplica:gte:2", "fileReplica", "1", nna.getLoader());
    assertThat(transformMap.size(), is(not(0)));
    Function<INode, Long> fileReplicaTransform = transformMap.get("fileReplica");
    assertThat(fileReplicaTransform, is(notNullValue()));
    for (INode node : nna.getLoader().getINodeSet("files")) {
      Long transformedFileReplica = fileReplicaTransform.apply(node);
      assertThat(transformedFileReplica, is(1L));
    }
  }

  @Test
  public void testTransformDiskspaceConsumedByReplFactor() {
    Map<String, Function<INode, Long>> transformMap =
        Transforms.getAttributeTransforms("fileReplica:gte:2", "fileReplica", "1", nna.getLoader());
    assertThat(transformMap.size(), is(not(0)));
    Function<INode, Long> fileReplicaTransform = transformMap.get("diskspaceConsumed");
    assertThat(fileReplicaTransform, is(notNullValue()));
    Collection<INode> files = nna.getLoader().getINodeSet("files");
    long diskspaceConsumed =
        files
            .stream()
            .mapToLong(node -> node.asFile().getFileReplication() * node.asFile().computeFileSize())
            .sum();
    long transformedDiskspaceConsumed = files.stream().mapToLong(fileReplicaTransform::apply).sum();
    assertThat(transformedDiskspaceConsumed < diskspaceConsumed, is(true));
  }

  @Test
  public void testTransformDiskspaceConsumedByUser() {
    Map<String, Function<INode, Long>> transformMap =
        Transforms.getAttributeTransforms("user:eq:hdfs", "fileReplica", "1", nna.getLoader());
    assertThat(transformMap.size(), is(not(0)));
    Function<INode, Long> fileReplicaTransform = transformMap.get("diskspaceConsumed");
    assertThat(fileReplicaTransform, is(notNullValue()));
    Collection<INode> files = nna.getLoader().getINodeSet("files");
    long diskspaceConsumed =
        files
            .stream()
            .mapToLong(node -> node.asFile().getFileReplication() * node.asFile().computeFileSize())
            .sum();
    long transformedDiskspaceConsumed = files.stream().mapToLong(fileReplicaTransform::apply).sum();
    assertThat(transformedDiskspaceConsumed < diskspaceConsumed, is(true));
  }

  @Test
  public void testTransformDiskspaceConsumedByBeingWritten() {
    Map<String, Function<INode, Long>> transformMap =
        Transforms.getAttributeTransforms(
            "isUnderConstruction:eq:true", "fileReplica", "1", nna.getLoader());
    assertThat(transformMap.size(), is(not(0)));
    Function<INode, Long> fileReplicaTransform = transformMap.get("diskspaceConsumed");
    assertThat(fileReplicaTransform, is(notNullValue()));
    Collection<INode> files = nna.getLoader().getINodeSet("files");
    long diskspaceConsumed =
        files
            .stream()
            .mapToLong(node -> node.asFile().getFileReplication() * node.asFile().computeFileSize())
            .sum();
    long transformedDiskspaceConsumed = files.stream().mapToLong(fileReplicaTransform::apply).sum();
    assertThat(transformedDiskspaceConsumed == diskspaceConsumed, is(true));
  }
}
