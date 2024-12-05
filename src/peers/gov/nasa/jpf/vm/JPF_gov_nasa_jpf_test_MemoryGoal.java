/*
 * Copyright (C) 2014, United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The Java Pathfinder core (jpf-core) platform is licensed under the
 * Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0. 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package gov.nasa.jpf.vm;

import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.NativePeer;

/**
 * Native peer for MemoryGoal tests, measuring memory allocation and deallocation during method execution.
 */
public class JPF_gov_nasa_jpf_test_MemoryGoal extends NativePeer {

  private Listener listener;

  // Listener to track memory allocation and deallocation
  static class Listener extends ListenerAdapter {

    private final MethodInfo methodInfo;
    private boolean active = false;

    private long allocatedBytes = 0;
    private long freedBytes = 0;
    private long allocationCount = 0;
    private long deallocationCount = 0;

    Listener(MethodInfo methodInfo) {
      this.methodInfo = methodInfo;
    }

    @Override
    public void objectCreated(VM vm, ThreadInfo threadInfo, ElementInfo elementInfo) {
      if (active) {
        allocationCount++;
        allocatedBytes += elementInfo.getHeapSize(); // Approximation of heap size
      }
    }

    @Override
    public void objectReleased(VM vm, ThreadInfo threadInfo, ElementInfo elementInfo) {
      if (active) {
        deallocationCount++;
        freedBytes += elementInfo.getHeapSize(); // Approximation of heap size
      }
    }

    @Override
    public void instructionExecuted(VM vm, ThreadInfo threadInfo, Instruction nextInstruction, Instruction executedInstruction) {
      if (!active) {
        if (executedInstruction.getMethodInfo() == methodInfo) {
          active = true;
        }
      } else {
        if (executedInstruction instanceof JVMReturnInstruction && executedInstruction.getMethodInfo() == methodInfo) {
          active = false;
        }
      }
    }

    // Calculates the total memory allocated minus the freed memory
    long getNetAllocatedBytes() {
      return allocatedBytes - freedBytes;
    }
  }

  @MJI
  public boolean preCheck(MJIEnv env, int objRef, int testContextRef, int methodRef) {
    MethodInfo methodInfo = JPF_java_lang_reflect_Method.getMethodInfo(env, methodRef);

    listener = new Listener(methodInfo);
    env.addListener(listener);
    return true;
  }

  @MJI
  public boolean postCheck(MJIEnv env, int objRef, int testContextRef, int methodRef, int resultRef, int exRef) {
    long maxGrowth = env.getLongField(objRef, "maxGrowth");

    Listener currentListener = listener;
    env.removeListener(currentListener);
    listener = null;

    // Check if memory growth is within the specified limit
    return currentListener.getNetAllocatedBytes() <= maxGrowth;
  }
}
 