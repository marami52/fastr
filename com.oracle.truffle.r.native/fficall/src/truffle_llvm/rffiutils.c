/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include <rffiutils.h>

SEXP unimplemented(char *name) {
	printf("unimplemented %s\n", name);
	void *nameString = truffle_read_string(name);
	void *obj = truffle_import_cached("_fastr_rffi_call");
	void *result = truffle_invoke(obj, "unimplemented", nameString);
	return result;
}

char *ensure_truffle_chararray(const char *x) {
	if (truffle_is_truffle_object(x)) {
		return (char *)x;
	} else {
		IMPORT_CALLHELPER_IMPL();
		return truffle_invoke(obj, "bytesToNativeCharArray", truffle_read_n_bytes(x, strlen(x)));
	}
}
