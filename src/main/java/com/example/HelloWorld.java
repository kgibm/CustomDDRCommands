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

import com.ibm.j9ddr.tools.ddrinteractive.Command;
import com.ibm.j9ddr.tools.ddrinteractive.Context;
import com.ibm.j9ddr.tools.ddrinteractive.DDRInteractiveCommandException;
import com.ibm.j9ddr.tools.ddrinteractive.annotations.DebugExtension;
import com.ibm.j9ddr.vm29.j9.DataType;
import com.ibm.j9ddr.vm29.pointer.generated.J9JavaVMPointer;
import com.ibm.j9ddr.vm29.pointer.helper.J9RASHelper;

@DebugExtension(VMVersion = "*")
public class HelloWorld extends Command {

	{
		@SuppressWarnings("unused")
		CommandDescription cd = addCommand("helloworld", "", "Test command");
	}

	@Override
	public void run(String command, String[] args, Context context, PrintStream out)
			throws DDRInteractiveCommandException {
		try {
			J9JavaVMPointer jvm = J9RASHelper.getVM(DataType.getJ9RASPointer());
			out.println("!j9javavm " + jvm.getHexAddress());
		} catch (Throwable t) {
			throw new DDRInteractiveCommandException("Error processing: " + t.getClass().getCanonicalName() + " " + t,
					t);
		}
	}
}
