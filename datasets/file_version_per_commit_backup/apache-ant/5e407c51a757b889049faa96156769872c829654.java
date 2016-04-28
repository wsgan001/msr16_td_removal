/*
 * Copyright  2001-2005 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.tools.ant.taskdefs;

import org.apache.tools.ant.BuildFileTest;
import org.apache.tools.ant.util.FileUtils;
import org.apache.tools.bzip2.CBZip2InputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 */
public class BZip2Test extends BuildFileTest {

    /** Utilities used for file operations */
    private static final FileUtils FILE_UTILS = FileUtils.getFileUtils();

    public BZip2Test(String name) {
        super(name);
    }

    public void setUp() {
        configureProject("src/etc/testcases/taskdefs/bzip2.xml");
        executeTarget("prepare");
    }

    public void tearDown() {
        executeTarget("cleanup");
    }

    public void testRealTest() throws IOException {
        executeTarget("realTest");

        // doesn't work: Depending on the compression engine used,
        // compressed bytes may differ. False errors would be
        // reported.
        // assertTrue("File content mismatch",
        // FILE_UTILS.contentEquals(project.resolveFile("expected/asf-logo-huge.tar.bz2"),
        // project.resolveFile("asf-logo-huge.tar.bz2")));

        // We have to compare the decompressed content instead:

        File originalFile =
            project.resolveFile("expected/asf-logo-huge.tar.bz2");
        File actualFile   = project.resolveFile("asf-logo-huge.tar.bz2");

        InputStream originalIn =
            new BufferedInputStream(new FileInputStream(originalFile));
        assertEquals((byte) 'B', originalIn.read());
        assertEquals((byte) 'Z', originalIn.read());

        InputStream actualIn =
            new BufferedInputStream(new FileInputStream(actualFile));
        assertEquals((byte) 'B', actualIn.read());
        assertEquals((byte) 'Z', actualIn.read());

        originalIn = new CBZip2InputStream(originalIn);
        actualIn   = new CBZip2InputStream(actualIn);

        while (true) {
            int expected = originalIn.read();
            int actual   = actualIn.read();
            if (expected >= 0) {
                if (expected != actual) {
                    fail("File content mismatch");
                }
            } else {
                if (actual >= 0) {
                    fail("File content mismatch");
                }
                break;
            }
        }

        originalIn.close();
        actualIn.close();
    }

    public void testResource(){
        executeTarget("realTestWithResource");
    }

    public void testDateCheck(){
        executeTarget("testDateCheck");
        String log = getLog();
        assertTrue(
            "Expecting message ending with 'asf-logo.gif.bz2 is up to date.' but got '" + log + "'",
            log.endsWith("asf-logo.gif.bz2 is up to date."));
    }

}