/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineForkchoiceUpdatedParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.Optional;

import io.vertx.core.Vertx;

public class EngineForkchoiceUpdatedV3 extends AbstractEngineForkchoiceUpdated {

  private final Optional<ScheduledProtocolSpec.Hardfork> cancun;

  public EngineForkchoiceUpdatedV3(
      final Vertx vertx,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator,
      final EngineCallListener engineCallListener) {
    super(vertx, protocolSchedule, protocolContext, mergeCoordinator, engineCallListener);
    this.cancun = protocolSchedule.hardforkFor(s -> s.fork().name().equalsIgnoreCase("Cancun"));
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_FORKCHOICE_UPDATED_V3.getMethodName();
  }

  @Override
  protected ValidationResult<RpcErrorType> validateParameter(
      final EngineForkchoiceUpdatedParameter fcuParameter) {
    if (fcuParameter.getHeadBlockHash() == null) {
      return ValidationResult.invalid(RpcErrorType.INVALID_PARAMS, "Missing head block hash");
    } else if (fcuParameter.getSafeBlockHash() == null) {
      return ValidationResult.invalid(RpcErrorType.INVALID_PARAMS, "Missing safe block hash");
    } else if (fcuParameter.getFinalizedBlockHash() == null) {
      return ValidationResult.invalid(RpcErrorType.INVALID_PARAMS, "Missing finalized block hash");
    }
    return ValidationResult.valid();
  }

  @Override
  protected ValidationResult<RpcErrorType> validateForkSupported(final long blockTimestamp) {
    if (protocolSchedule.isPresent()) {
      if (cancun.isPresent() && blockTimestamp >= cancun.get().milestone()) {
        return ValidationResult.valid();
      } else {
        return ValidationResult.invalid(
            RpcErrorType.UNSUPPORTED_FORK,
            "Cancun configured to start at timestamp: " + cancun.get().milestone());
      }
    } else {
      return ValidationResult.invalid(
          RpcErrorType.UNSUPPORTED_FORK, "Configuration error, no schedule for Cancun fork set");
    }
  }
}
