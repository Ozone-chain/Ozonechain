/*
 * Copyright contributors to Hyperledger Besu
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
 *
 */

package org.hyperledger.besu.evm.tracing;

import static com.google.common.base.Strings.padStart;

import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.worldstate.WorldView;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Joiner;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/** The Standard json tracer. */
public class StandardJsonTracer implements OperationTracer {

  private static final Joiner commaJoiner = Joiner.on(',');
  private final PrintWriter out;
  private final boolean showMemory;
  private final boolean showStack;
  private final boolean showReturnData;
  private int pc;
  private int section;
  private List<String> stack;
  private String gas;
  private Bytes memory;
  private int memorySize;
  private int depth;

  /**
   * Instantiates a new Standard json tracer.
   *
   * @param out the out
   * @param showMemory show memory in trace lines
   * @param showStack show the stack in trace lines
   * @param showReturnData show return data in trace lines
   */
  public StandardJsonTracer(
      final PrintWriter out,
      final boolean showMemory,
      final boolean showStack,
      final boolean showReturnData) {
    this.out = out;
    this.showMemory = showMemory;
    this.showStack = showStack;
    this.showReturnData = showReturnData;
  }

  /**
   * Instantiates a new Standard json tracer.
   *
   * @param out the out
   * @param showMemory show memory in trace lines
   * @param showStack show the stack in trace lines
   * @param showReturnData show return data in trace lines
   */
  public StandardJsonTracer(
      final PrintStream out,
      final boolean showMemory,
      final boolean showStack,
      final boolean showReturnData) {
    this(new PrintWriter(out, true, StandardCharsets.UTF_8), showMemory, showStack, showReturnData);
  }

  /**
   * Short as hex string.
   *
   * @param number the number
   * @return the string
   */
  public static String shortNumber(final UInt256 number) {
    return number.isZero() ? "0x0" : number.toShortHexString();
  }

  /**
   * Long number as hex string.
   *
   * @param number the number
   * @return the string
   */
  public static String shortNumber(final long number) {
    return "0x" + Long.toHexString(number);
  }

  private static String shortBytes(final Bytes bytes) {
    return bytes.isZero() ? "0x0" : bytes.toShortHexString();
  }

  @Override
  public void tracePreExecution(final MessageFrame messageFrame) {
    stack = new ArrayList<>(messageFrame.stackSize());
    for (int i = messageFrame.stackSize() - 1; i >= 0; i--) {
      stack.add("\"" + shortBytes(messageFrame.getStackItem(i)) + "\"");
    }
    pc =
        messageFrame.getPC()
            - messageFrame.getCode().getCodeSection(messageFrame.getSection()).getEntryPoint();
    section = messageFrame.getSection();
    gas = shortNumber(messageFrame.getRemainingGas());
    memorySize = messageFrame.memoryWordSize() * 32;
    if (showMemory && memorySize > 0) {
      memory = messageFrame.readMemory(0, messageFrame.memoryWordSize() * 32L);
    } else {
      memory = null;
    }
    depth = messageFrame.getMessageStackSize();
  }

  @Override
  public void tracePostExecution(
      final MessageFrame messageFrame, final Operation.OperationResult executeResult) {
    final Operation currentOp = messageFrame.getCurrentOperation();
    final int opcode = currentOp.getOpcode();
    final Bytes returnData = messageFrame.getReturnData();

    final StringBuilder sb = new StringBuilder(1024);
    sb.append("{");
    sb.append("\"pc\":").append(pc).append(",");
    if (section > 0) {
      sb.append("\"section\":").append(section).append(",");
    }
    sb.append("\"op\":").append(opcode).append(",");
    sb.append("\"gas\":\"").append(gas).append("\",");
    sb.append("\"gasCost\":\"").append(shortNumber(executeResult.getGasCost())).append("\",");
    if (memory != null) {
      sb.append("\"memory\":\"").append(memory.toHexString()).append("\",");
    }
    sb.append("\"memSize\":").append(memorySize).append(",");
    if (showStack) {
      sb.append("\"stack\":[").append(commaJoiner.join(stack)).append("],");
    }
    if (showReturnData && returnData.size() > 0) {
      sb.append("\"returnData\":\"").append(returnData.toHexString()).append("\",");
    }
    sb.append("\"depth\":").append(depth).append(",");
    sb.append("\"refund\":").append(messageFrame.getGasRefund()).append(",");
    sb.append("\"opName\":\"").append(currentOp.getName()).append("\"");
    if (executeResult.getHaltReason() != null) {
      sb.append(",\"error\":\"")
          .append(executeResult.getHaltReason().getDescription())
          .append("\"}");
    } else if (messageFrame.getRevertReason().isPresent()) {
      sb.append(",\"error\":\"")
          .append(quoteEscape(messageFrame.getRevertReason().orElse(Bytes.EMPTY)))
          .append("\"}");
    } else {
      sb.append("}");
    }
    out.println(sb);
  }

  private static String quoteEscape(final Bytes bytes) {
    final StringBuilder result = new StringBuilder(bytes.size());
    for (final byte b : bytes.toArrayUnsafe()) {
      final int c = Byte.toUnsignedInt(b);
      // list from RFC-4627 section 2
      if (c == '"') {
        result.append("\\\"");
      } else if (c == '\\') {
        result.append("\\\\");
      } else if (c == '/') {
        result.append("\\/");
      } else if (c == '\b') {
        result.append("\\b");
      } else if (c == '\f') {
        result.append("\\f");
      } else if (c == '\n') {
        result.append("\\n");
      } else if (c == '\r') {
        result.append("\\r");
      } else if (c == '\t') {
        result.append("\\t");
      } else if (c <= 0x1F) {
        result.append("\\u");
        result.append(padStart(Integer.toHexString(c), 4, '0'));
      } else {
        result.append((char) b);
      }
    }
    return result.toString();
  }

  @Override
  public void tracePrecompileCall(
      final MessageFrame frame, final long gasRequirement, final Bytes output) {
    // precompile calls are not part of the standard trace
  }

  @Override
  public void traceAccountCreationResult(
      final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
    // precompile calls are not part of the standard trace
  }

  @Override
  public void traceEndTransaction(
      final WorldView _worldView,
      final Transaction _tx,
      final boolean _status,
      final Bytes output,
      final List<Log> _logs,
      final long gasUsed,
      final long timeNs) {
    final StringBuilder sb = new StringBuilder(1024);
    sb.append("{");
    if (output.size() > 0) {
      sb.append("\"output\":\"").append(output.toShortHexString()).append("\",");
    } else {
      sb.append("\"output\":\"\",");
    }
    sb.append("\"gasUsed\":\"").append(Words.longBytes(gasUsed).toShortHexString()).append("\",");
    sb.append("\"time\":").append(timeNs).append("}");
    out.println(sb);
  }
}
