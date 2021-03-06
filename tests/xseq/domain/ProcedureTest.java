/*
 * XML Sequences for mission critical IT procedures
 *
 * Copyright © 2005-2010 Operational Dynamics Consulting, Pty Ltd
 *
 * The code in this file, and the program it is a part of, is made available
 * to you by its authors as open source software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License version
 * 2 ("GPL") as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GPL for more details.
 *
 * You should have received a copy of the GPL along with this program. If not,
 * see http://www.gnu.org/licenses/. The authors of this program may be
 * contacted through http://research.operationaldynamics.com/projects/xseq/.
 */
package xseq.domain;

import junit.framework.TestCase;

/**
 * Exercise the Procedure class's ID string navigation.
 * 
 * @author Andrew Cowie
 */
public class ProcedureTest extends TestCase
{
    String xml = null;

    Procedure p = null;

    public void setUp() {
        xml = "<procedure>" + "<section>" + "<step>" + "<name who=\"joe\">" + "<task>Blah</task>"
                + "<task>Fee fi fo fum</task>" + "</name>" + "<name who=\"fred\">"
                + "<task>Bling</task>" + "<task>MoreBling</task>" + "</name>" + "</step>"
                + "<step><name who=\"scarlet\"><task>Jumping up and down</task></name></step>"
                + "</section>" + "</procedure>";

        p = new Procedure(xml);
    }

    public void testTaskSibblings() {
        String nextTaskId = p.setTaskAsDone("n4");
        assertEquals("n5", nextTaskId);

        String furtherTaskId = p.setTaskAsDone("n5");
        assertNull(furtherTaskId);
    }

    public void testIsNameDone() {
        assertFalse(p.isNameDone("n4"));
        assertFalse(p.isNameDone("n5"));
        p.setTaskAsDone("n4");
        assertFalse(p.isNameDone("n4"));
        assertFalse(p.isNameDone("n5"));
        p.setTaskAsDone("n5");
        assertTrue(p.isNameDone("n4"));
        assertTrue(p.isNameDone("n5"));
    }

    public void testIsStepDone() {
        assertFalse(p.isNameDone("n7"));
        assertFalse(p.isNameDone("n8"));

        assertFalse(p.isStepDone("n4"));
        assertFalse(p.isStepDone("n5"));
        assertFalse(p.isStepDone("n7"));
        assertFalse(p.isStepDone("n8"));

        p.setTaskAsDone("n4");
        p.setTaskAsDone("n5");

        assertFalse(p.isStepDone("n4"));
        assertFalse(p.isStepDone("n5"));
        assertFalse(p.isStepDone("n7"));
        assertFalse(p.isStepDone("n8"));

        p.setTaskAsDone("n7");

        assertFalse(p.isStepDone("n4"));
        assertFalse(p.isStepDone("n5"));
        assertFalse(p.isStepDone("n7"));
        assertFalse(p.isStepDone("n8"));

        p.setTaskAsDone("n8");

        assertTrue(p.isStepDone("n4"));
        assertTrue(p.isStepDone("n5"));
        assertTrue(p.isStepDone("n7"));
        assertTrue(p.isStepDone("n8"));
    }

    public void testSectionAndProcedureDone() {
        p.setTaskAsDone("n4");
        p.setTaskAsDone("n5");
        p.setTaskAsDone("n7");
        p.setTaskAsDone("n8");
        p.setTaskAsDone("n11");

        assertTrue(p.isSectionDone("n4"));
        assertTrue(p.isSectionDone("n5"));
        assertTrue(p.isSectionDone("n7"));
        assertTrue(p.isSectionDone("n8"));

        assertTrue(p.isProcedureDone("n4"));
        assertTrue(p.isProcedureDone("n5"));
        assertTrue(p.isProcedureDone("n7"));
        assertTrue(p.isProcedureDone("n8"));
    }

    public void testParentMethods() {
        assertEquals("n3", p.getParentId("n4", "name"));
        assertEquals("n2", p.getParentId("n4", "step"));
        assertEquals("n1", p.getParentId("n4", "section"));
        assertEquals("n0", p.getParentId("n4", "procedure"));
    }

    public void testFirstTask() {
        String taskId = p.getFirstTaskId("n0");
        assertEquals("n4", taskId);

        taskId = p.getFirstTaskId("n2", "joe");
        assertEquals("n4", taskId);

        taskId = p.getFirstTaskId("n2", "fred");
        assertEquals("n7", taskId);

        taskId = p.getFirstTaskId("n2", "sammy");
        assertNull(taskId);
    }

    public void testNextStep() {
        String nextStepId = p.getNextStepId("n2");
        assertEquals("n9", nextStepId);
        nextStepId = p.getNextStepId("n9");
        assertNull(nextStepId);
    }

    public void testMyTask() {
        assertTrue(p.isTaskMine("n4", "joe"));
        assertFalse(p.isTaskMine("n7", "joe"));
        assertTrue(p.isTaskMine("n7", "fred"));
    }

    public void testIsTaskDone() {
        assertFalse(p.isTaskDone("n4"));
        p.setTaskAsDone("n4");
        assertTrue(p.isTaskDone("n4"));
    }
}
