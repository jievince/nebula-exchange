/* Copyright (c) 2020 vesoft inc. All rights reserved.
 *
 * This source code is licensed under Apache 2.0 License,
 * attached with Common Clause Condition 1.0, found in the LICENSES directory.
 */

package com.vesoft.nebula.exchange

import com.google.common.net.HostAndPort
import com.vesoft.nebula.client.graph.NebulaPoolConfig
import com.vesoft.nebula.client.graph.data.{
  CASignedSSLParam,
  HostAddress,
  ResultSet,
  SSLParam,
  SelfSignedSSLParam
}
import com.vesoft.nebula.client.graph.net.{NebulaPool, Session}
import com.vesoft.nebula.exchange.config.{SslConfigEntry, SslType, UserConfigEntry}
import org.apache.log4j.Logger

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

/**
  * GraphProvider for Nebula Graph Service
  */
class GraphProvider(addresses: List[HostAndPort], timeout: Int, sslConfigEntry: SslConfigEntry)
    extends AutoCloseable
    with Serializable {
  private[this] lazy val LOG = Logger.getLogger(this.getClass)

  @transient val nebulaPoolConfig = new NebulaPoolConfig
  @transient val pool: NebulaPool = new NebulaPool
  val address                     = new ListBuffer[HostAddress]()
  for (addr <- addresses) {
    address.append(new HostAddress(addr.getHostText, addr.getPort))
  }
  val randAddr = scala.util.Random.shuffle(address)

  nebulaPoolConfig.setTimeout(timeout)

  // config graph ssl
  nebulaPoolConfig.setEnableSsl(sslConfigEntry.enableGraph)
  if (sslConfigEntry.enableGraph) {
    var sslParam: SSLParam = null
    if (sslConfigEntry.signType == SslType.CA) {
      val ca = sslConfigEntry.caSignParam
      sslParam = new CASignedSSLParam(ca.caCrtFilePath, ca.crtFilePath, ca.keyFilePath)
    } else {
      val self = sslConfigEntry.selfSignParam
      sslParam = new SelfSignedSSLParam(self.crtFilePath, self.keyFilePath, self.password)
    }
    nebulaPoolConfig.setSslParam(sslParam)
  }

  pool.init(randAddr.asJava, nebulaPoolConfig)

  def getGraphClient(userConfigEntry: UserConfigEntry): Session = {
    pool.getSession(userConfigEntry.user, userConfigEntry.password, true);
  }

  def releaseGraphClient(session: Session): Unit = {
    session.release()
  }

  override def close(): Unit = {
    pool.close()
  }

  def switchSpace(session: Session, space: String): ResultSet = {
    val switchStatment = s"use $space"
    LOG.info(s"switch space $space")
    val result = submit(session, switchStatment)
    result
  }

  def submit(session: Session, statement: String): ResultSet = {
    session.execute(statement)
  }
}
