/*
 * Copyright 2021 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import com.ibm.j9ddr.tools.ddrinteractive.Command;
import com.ibm.j9ddr.tools.ddrinteractive.Context;
import com.ibm.j9ddr.tools.ddrinteractive.DDRInteractiveCommandException;
import com.ibm.j9ddr.tools.ddrinteractive.annotations.DebugExtension;
import com.ibm.j9ddr.vm29.j9.DataType;
import com.ibm.j9ddr.vm29.pointer.PointerPointer;
import com.ibm.j9ddr.vm29.pointer.StructurePointer.StructureField;
import com.ibm.j9ddr.vm29.pointer.U64Pointer;
import com.ibm.j9ddr.vm29.pointer.VoidPointer;
import com.ibm.j9ddr.vm29.pointer.generated.J9JavaStackPointer;
import com.ibm.j9ddr.vm29.pointer.generated.J9JavaVMPointer;
import com.ibm.j9ddr.vm29.pointer.generated.J9PortLibraryPointer;
import com.ibm.j9ddr.vm29.pointer.generated.J9VMThreadPointer;
import com.ibm.j9ddr.vm29.pointer.generated.OMRPortLibraryGlobalDataPointer;
import com.ibm.j9ddr.vm29.pointer.generated.OMRPortLibraryPointer;
import com.ibm.j9ddr.vm29.pointer.generated.OMRPortPlatformGlobalsPointer;
import com.ibm.j9ddr.vm29.pointer.helper.J9RASHelper;
import com.ibm.j9ddr.vm29.types.U64;

@DebugExtension(VMVersion = "*")
public class BelowTheBar extends Command {

	{
		@SuppressWarnings("unused")
		CommandDescription cd = addCommand("belowthebar", "<subAllocHeapMem32Offset>",
				"Print native memory information below the 4GB bar (or 2GB for z/OS). subAllocHeapMem32Offset is required for older JDKs. Run without the option to show the command to use.");
	}

	private static final DecimalFormat formatter = new DecimalFormat("#,###");
	private static final long BYTES_KB = 1024;
	private static final long BYTES_MB = BYTES_KB * BYTES_KB;
	private static final long BYTES_GB = BYTES_MB * BYTES_KB;
	private static final long BYTES_TB = BYTES_GB * BYTES_KB;

	@Override
	public void run(String command, String[] args, Context context, PrintStream out)
			throws DDRInteractiveCommandException {
		try {
			boolean debug = false;
			long subAllocHeapMem32Offset = -1;

			if (args != null) {
				for (int i = 0; i < args.length; i++) {
					if ("debug".equals(args[i])) {
						debug = true;
					}
				}
			}

			/*-
			> !findvm
			!j9javavm 0x000014BFC40227B0			
			 */
			J9JavaVMPointer jvm = J9RASHelper.getVM(DataType.getJ9RASPointer());

			J9PortLibraryPointer portLibrary = jvm.portLibrary();
			OMRPortLibraryPointer omrPortLibrary = portLibrary.omrPortLibrary();
			OMRPortLibraryGlobalDataPointer portGlobals = omrPortLibrary.portGlobals();
			OMRPortPlatformGlobalsPointer platformGlobals = portGlobals.platformGlobals();

			for (StructureField field : platformGlobals.getStructureFields()) {
				if ("subAllocHeapMem32".equals(field.name)) {
					subAllocHeapMem32Offset = field.offset;
					break;
				}
			}

			// Older JDKs don't have subAllocHeapMem32 annotated:
			// https://github.com/eclipse/openj9/pull/12543
			if (subAllocHeapMem32Offset < 0) {
				if (args != null) {
					for (int i = 0; i < args.length; i++) {
						if ("debug".equals(args[i])) {
							debug = true;
						} else {
							String subAllocHeapMem32OffsetString = args[i];
							if (subAllocHeapMem32OffsetString.toLowerCase().startsWith("0x")) {
								subAllocHeapMem32Offset = Long.parseLong(subAllocHeapMem32OffsetString.substring(2),
										16);
							} else {
								subAllocHeapMem32Offset = Long.parseLong(subAllocHeapMem32OffsetString);
							}
						}
					}
				}
			}

			if (subAllocHeapMem32Offset < 0) {
				throw new DDRInteractiveCommandException(
						"Cannot find subAllocHeapMem32 offset for !omrportplatformglobals "
								+ platformGlobals.getHexAddress());
			}

			/*-
			J9SubAllocateHeapMem32 at 0x14bfc401ee28 {
			Fields for J9SubAllocateHeapMem32:
			0x0: U64 totalSize = 0x0000000003200000 (52428800)
			0x8: struct J9HeapWrapper* firstHeapWrapper = !j9heapwrapper 0x000014BFC4058660
			0x10: struct J9ThreadMonitor* monitor = !j9threadmonitor 0x000014BFC4019678
			0x18: U64 subCommitCommittedMemorySize = 0x0000000003200000 (52428800)
			0x20: U32 canSubCommitHeapGrow = 0x00000001 (1)
			0x28: struct J9HeapWrapper* subCommitHeapWrapper = !j9heapwrapper 0x000014BFC4058660
			0x30: U64 suballocator_initialSize = 0x000000000C800000 (209715200)
			0x38: U64 suballocator_commitSize = 0x0000000003200000 (52428800)
			}
			*/
			PointerPointer subAllocateHeapMem = PointerPointer
					.cast(platformGlobals.getAddress() + subAllocHeapMem32Offset);

			out.println("Below the bar analysis for !j9suballocateheapmem32 " + subAllocateHeapMem.getHexAddress());
			out.println();

			U64 subCommitCommittedMemorySize = U64Pointer.cast(subAllocateHeapMem.getAddress() + 0x18).at(0);

			if (debug)
				printSeparator(out);

			/*-
			J9HeapWrapper at 0x14bfc4058660 {
			Fields for J9HeapWrapper:
			0x0: struct J9HeapWrapper* nextHeapWrapper = !j9heapwrapper 0x0000000000000000
			0x8: struct J9Heap* heap = !j9heap 0x0000000000010000
			0x10: U64 heapSize = 0x000000000C800000 (209715200)
			0x18: struct J9PortVmemIdentifier* vmemID = !j9portvmemidentifier 0x000014BFC40585D0
			}			
			*/
			VoidPointer heapWrapper = subAllocateHeapMem.at(1);

			List<long[]> suballocatedHeaps = new ArrayList<>();

			// TODO: walk the heaps and measure the fragmentation:
			// https://github.com/eclipse/omr/blob/master/port/common/omrheap.c, which uses
			// "the first-fit method in KNUTH, D. E. The Art of Computer Programming. Vol.
			// 1: Fundamental Algorithms. (2nd edition). Addison-Wesley, Reading, Mass.,
			// 1973, Sect. 2.5."

			long totalJ9HeapWrapperSize = 0, totalJ9HeapSize = 0;
			while (heapWrapper.notNull()) {

				// The J9HeapWrapper->heapSize is in bytes. The J9Heap->size is the number of 8
				// byte slots, since the memory is managed in 8 byte chunks.

				U64 heapSize = U64Pointer.cast(heapWrapper.getAddress() + 0x10).at(0);

				/*-
				J9Heap at 0x10000 {
				Fields for J9Heap:
				0x0: U64 heapSize = 0x0000000000640000 (6553600)
				0x8: U64 firstFreeBlock = 0x0000000000000FFA (4090)
				0x10: U64 lastAllocSlot = 0x000000000002D91A (186650)
				0x18: U64 largestAllocSizeVisited = 0x0000000000000007 (7)
				}
				*/
				PointerPointer j9Heap = PointerPointer.cast(PointerPointer.cast(heapWrapper).at(1));
				U64 j9HeapSize = U64Pointer.cast(j9Heap.getAddress() + 0x0).at(0);

				// The J9HeapWrapper->heapSize is in bytes. The J9Heap->size is the number of 8
				// byte slots, since the memory is managed in 8 byte chunks. They are both
				// referring to the same memory
				long j9HeapSizeCalculated = j9HeapSize.longValue() * 8;

				totalJ9HeapWrapperSize += heapSize.longValue();
				totalJ9HeapSize += j9HeapSizeCalculated;

				if (debug)
					out.println("J9HeapWrapper " + heapWrapper.getHexAddress() + " ; J9HeapWrapper size "
							+ formatter.format(heapSize.longValue()) + " ; J9Heap " + j9Heap.getHexAddress()
							+ " ; J9Heap size " + formatter.format(j9HeapSizeCalculated));
				else
					out.println("!j9heapwrapper " + heapWrapper.getHexAddress() + " @ " + j9Heap.getHexAddress() + " - "
							+ String.format("0x%016X", j9Heap.getAddress() + heapSize.longValue()) + " ("
							+ getByteInfo(heapSize.longValue()) + ")");

				suballocatedHeaps.add(new long[] { j9Heap.getAddress(), j9Heap.getAddress() + heapSize.longValue() });

				heapWrapper = VoidPointer.cast(PointerPointer.cast(heapWrapper).at(0));
			}

			if (debug) {
				out.println(System.lineSeparator() + "  subCommitCommittedMemorySize = "
						+ formatter.format(subCommitCommittedMemorySize.longValue()));
				out.println("  totalJ9HeapWrapperSize = " + formatter.format(totalJ9HeapWrapperSize)
						+ " ; totalJ9HeapSize = " + formatter.format(totalJ9HeapSize));
				printSeparator(out);
			} else {
				out.println("--");
				out.println("Total = " + getByteInfo(totalJ9HeapWrapperSize));
			}

			J9VMThreadPointer mainThread = jvm.mainThread();
			J9VMThreadPointer thread = mainThread;
			long numThreads = 0, totalThreadStacks = 0;
			do {
				J9JavaStackPointer stack = thread.stackObject();

				long stackPointerAddress = stack.getAddress();

				long totalSize = stack.end().getAddress() - stack.getAddress();

				if (debug)
					out.println("J9VMThreadPointer " + stack.getHexAddress() + " - " + stack.end().getHexAddress()
							+ " ; size " + formatter.format(stack.size().longValue()) + " ; diff "
							+ formatter.format(totalSize));

				numThreads++;
				totalThreadStacks += totalSize;

				boolean foundHeapRange = false;
				for (long[] range : suballocatedHeaps) {
					if (stackPointerAddress >= range[0] && stackPointerAddress <= range[1]) {
						foundHeapRange = true;
					}
				}
				if (!foundHeapRange) {
					out.println("  Stack outside known heaps");
				}

				thread = thread.linkNext();
			} while (thread.notNull() && !thread.equals(mainThread));

			if (debug)
				out.println(System.lineSeparator() + "  numThreads = " + formatter.format(numThreads)
						+ " ; totalThreadStacks = " + formatter.format(totalThreadStacks));
			else {
				out.println("  " + numThreads + " threads (" + getByteInfo(totalThreadStacks) + ")");
			}

		} catch (Throwable t) {
			t.printStackTrace();
			throw new DDRInteractiveCommandException("Error processing: " + t.getClass().getCanonicalName() + " " + t,
					t);
		}
	}

	private String getByteInfo(long val) {
		String result = val + " bytes / ";
		if (val >= BYTES_TB) {
			result += (val / BYTES_TB) + " TB";
		} else if (val >= BYTES_GB) {
			result += (val / BYTES_GB) + " GB";
		} else if (val >= BYTES_MB) {
			result += (val / BYTES_MB) + " MB";
		} else if (val >= BYTES_KB) {
			result += (val / BYTES_KB) + " KB";
		}
		return result;
	}

	private void printSeparator(PrintStream out) {
		out.println(System.lineSeparator() + "==============" + System.lineSeparator());
	}
}
