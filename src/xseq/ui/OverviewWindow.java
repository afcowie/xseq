/*
 * XML Sequences for mission critical IT procedures
 *
 * Copyright © 2004-2010 Operational Dynamics Consulting, Pty Ltd
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
package xseq.ui;

import generic.util.Debug;
import generic.util.DebugException;

import java.io.FileNotFoundException;

import org.gnome.gdk.Event;
import org.gnome.glade.Glade;
import org.gnome.glade.XML;
import org.gnome.gtk.CellRendererText;
import org.gnome.gtk.DataColumn;
import org.gnome.gtk.DataColumnString;
import org.gnome.gtk.ListStore;
import org.gnome.gtk.ToolButton;
import org.gnome.gtk.TreeIter;
import org.gnome.gtk.TreePath;
import org.gnome.gtk.TreeView;
import org.gnome.gtk.TreeViewColumn;
import org.gnome.gtk.Widget;
import org.gnome.gtk.Window;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import xseq.client.ProcedureClient;
import xseq.domain.Procedure;
import xseq.services.XmlUtils;

/**
 * The window which displays the overview of the procedure currently being
 * exectuted. Note that we only instantiate one of these, as this window's UI
 * provides for "moving to the next section" and "forward/back buttons".
 * 
 * @author Andrew Cowie
 */
public class OverviewWindow
{
    private XML glade = null;

    private Window top = null;

    private DataColumnString summary_DataColumn;

    private TreeView view = null;

    private ToolButton prev_toolbutton = null;

    private ToolButton current_toolbutton = null;

    private ToolButton next_toolbutton = null;

    private int _numSections;

    /*
     * The entire program has only one procedure open at a time. [FUTURE Maybe
     * not? - This should be a member of the (a?) MasterWindow if more than
     * one OverviewWindow can be open? If so, then we'll need to add
     * get/setModels, and move the create code below elsewhere.]
     */

    /*
     * The instantiated TreeModel (as impleneted by ListStore). Doesn't need
     * to be static, for if we ever do instantiate another OverviewWindow, we
     * can just give it the same model (via a set) having get'd it from here.
     * awkward name, but it needs to be identifiable otherwise the setValue
     * calls are gibberish; this API is horrid enough as it is; its ok here,
     * but in any table with multiple columns it quickly gets right out of
     * hand.
     */
    private ListStore sectionModel = null;

    private TreeViewColumn[] sectionTreeViewColumns = null;

    OverviewWindow() {
        /*
         * Setup the underlying TreeModel
         */
        summary_DataColumn = new DataColumnString();

        sectionModel = new ListStore(new DataColumn[] {
            summary_DataColumn
        });
    }

    /**
     * Create an OverviewWindow based on the procedure in doc.
     * 
     * @param doc
     *            a DOM Document containing the procedure to be summarized and
     *            displayed.
     */
    public OverviewWindow(Procedure p) {
        this();
        createSectionModel(p.getDOM());

        /**
         * The glade code to instantiate the Gtk window and attach basic
         * handlers. The more specific XML processing logic is in the public
         * constructor, above, which calls this.
         */

        try {
            glade = Glade.parse("share/overview.glade", null);
        } catch (FileNotFoundException e) {
            // If it can't find that glade file, we have an app
            // configuration problem or worse some UI bug, and need to abort.
            e.printStackTrace();
            ProcedureClient.abort("Can't find glade file for OverviewWindow.");
        } catch (Exception e) {
            e.printStackTrace();
            ProcedureClient.abort("An internal error occured trying to read and process the glade file for the OverviewWindow.");
        }
        top = (Window) glade.getWidget("overview");
        top.hide();

        top.connect(new Window.DeleteEvent() {
            public boolean onDeleteEvent(Widget source, Event event) {
                Debug.print("listeners", "calling end_program() to initiate app termination");
                close_window();
                return false;
            }
        });

        prev_toolbutton = (ToolButton) glade.getWidget("prev_toolbutton");
        current_toolbutton = (ToolButton) glade.getWidget("current_toolbutton");
        next_toolbutton = (ToolButton) glade.getWidget("next_toolbutton");

        view = (TreeView) glade.getWidget("summary_treeview");
        view.setModel(sectionModel);

        TreeViewColumn viewColumn0 = view.appendColumn();
        sectionTreeViewColumns = new TreeViewColumn[] {
            viewColumn0
        };
        // Nastiest API since EJB
        viewColumn0.setClickable(true);
        viewColumn0.setResizable(true);
        viewColumn0.setReorderable(false);
        CellRendererText renderer0 = new CellRendererText(viewColumn0);
        renderer0.setMarkup(summary_DataColumn);

        view.setRulesHint(true);
        view.setEnableSearch(true);
        // this is the one that prevents the thing
        // turning into a drag n drop hierarchical
        // mess
        view.setReorderable(false);

        view.connect(new TreeView.RowActivated() {
            public void onRowActivated(TreeView source, TreePath path, TreeViewColumn vertical) {
                int i = Integer.parseInt(path.toString());

                /*
                 * Quite often the DetailsWindow will have been obscured. So
                 * we present it.
                 */
                ProcedureClient.ui._details.top.present();
                ProcedureClient.ui.activateSection(i);
            }
        });

        top.resize(1, 400);
        top.move(10, 5);

        /*
         * And start up at the first section.
         */
        activateSection(0, true);
        top.present();
    }

    public void close_window() {
        top.hide();
        // TODO testing only. REMOVE
        if (ProcedureClient.ui != null) {
            ProcedureClient.ui.shutdown();
        }
    }

    /**
     * Given a DOM Document of our procedure, iterate through and instanitate
     * the data backends that will be used by the OverviewWindow.
     * 
     * <P>
     * The OverviewWindow is essentially a condensed summation of <section>and
     * <step>Elements. So we get the <sections>, and then iterate through them
     * to get the groups of steps, which we combine into a fancy Pango markup
     * label and stick it into the Model cell.
     */
    void createSectionModel(Document dom) {

        /*
         * Get the section elements and walk through them
         */

        NodeList sections = dom.getElementsByTagName("section");
        _numSections = sections.getLength();

        for (int i = 0; i < _numSections; i++) {
            Element section = (Element) sections.item(i);

            String summary = sectionToPango(section);

            TreeIter iter = sectionModel.appendRow();
            sectionModel.setValue(iter, summary_DataColumn, summary);
        }
    }

    /**
     * Given a <section>Element, generate the Label which summarizes the
     * details of the <steps>in that <section>. There is Pango Markup here!!!
     * 
     * HARDCODE This is really tightly tied to the procedure DTD, getting
     * titles from attributes, etc.
     */
    private static String sectionToPango(Element section) {
        StringBuffer text = new StringBuffer();

        /*
         * First the title.
         */
        text.append("<big>");
        text.append(section.getAttribute("num"));
        text.append(". ");
        text.append(section.getAttribute("title"));
        text.append("</big>\n");

        /*
         * Now the precis
         */

        // If a <precis> is present in a <section>, it has to be first by the
        // current DTD. Yes, this is clumsy.
        NodeList precisList = section.getElementsByTagName("precis");

        if (precisList.getLength() == 1) {
            Node precis = precisList.item(0);
            precis.normalize();

            text.append("<i>");
            // TODO this is probably not sufficient if there are any child
            // nodes
            // besides the text node
            Node child = precis.getFirstChild();

            if (child.getNodeType() != Node.TEXT_NODE) {
                throw new DebugException("FIXME Not a Text Node");
            }

            String str = precis.getFirstChild().getNodeValue();
            if (str == null) {
                throw new DebugException("FIXME The Text Node was empty. (isn't that allowed?)");
            }

            /*
             * Clean up the node. TODO, generalize this; we'll need it again.
             */

            StringBuffer buf = new StringBuffer(str);
            int index;
            while ((index = buf.indexOf("\n")) != -1) {
                buf.setCharAt(index, ' ');
            }
            while ((index = buf.indexOf("\t")) != -1) {
                buf.setCharAt(index, ' ');
            }
            /*
             * trim. Yes, I know about String.trim(), but we've already done
             * half the work it does; no need to be inefficient.
             */
            while (buf.charAt(0) == ' ') {
                buf.deleteCharAt(0);
            }
            while (buf.charAt(buf.length() - 1) == ' ') {
                buf.deleteCharAt(buf.length() - 1);
            }

            while ((index = buf.indexOf("  ")) != -1) {
                buf.deleteCharAt(index);
            }

            /*
             * Word wrap. Unfortunately, Pango markup has no syntax for
             * expressing auto word wrap, and worse, GtkCellRendererText has
             * no ability to wrap text. So we (ick) do it by hand.
             */
            int next_space = 0;
            int line_start = 0;

            while (next_space != -1) {
                if ((next_space - line_start) > 50) { // HARDODE
                    buf.setCharAt(next_space, '\n');
                    line_start = next_space;
                }
                next_space = buf.indexOf(" ", next_space + 1); // bounds?
            }

            str = buf.toString();

            text.append(str);
            text.append("</i>\n");
        }
        // otherwise, we got must have got a <section> Element, but no matter.

        /*
         * Now the text of the step titles.
         */

        NodeList steps = section.getElementsByTagName("step");
        int num_steps = steps.getLength();

        for (int i = 0; i < num_steps; i++) {
            Element step = (Element) steps.item(i);
            text.append("\t"); // or some such spacer.
            text.append(step.getAttribute("num"));
            text.append(". ");
            NodeList titles = step.getElementsByTagName("title");
            Element title = (Element) titles.item(0);
            text.append(XmlUtils.getElementText(title).trim());
            text.append("\n");
        }

        return text.toString();
    }

    /**
     * This method, like all the activate{Prev,Next} methods, are the
     * callbacks invoked by the clicked handlers (as spec'd in the .glade
     * file). It calls the ProcedureUserInterface method of the same name,
     * which then callsback to this and other Window classes to affect the
     * necessary changes.
     */
    // have to be public so libglade signal connect can find them.
    public void activatePrevSection_cb() {
        // This will callback to the activateSection() routine here, but it
        // will
        // also call other Windows' activateSection routines.
        ProcedureClient.ui.activatePrevSection();
    }

    public void activateCurrentSection_cb() {
        // This will callback to the activateSection() routine here, but it
        // will
        // also call other Windows' activateSection routines.
        ProcedureClient.ui.activateCurrentSection();
    }

    public void activateNextSection_cb() {
        // This will callback to the activateSection() routine here, but it
        // will
        // also call other Windows' activateSection routines.
        ProcedureClient.ui.activateNextSection();
    }

    /*
     * Glade callbacks are nice, but not 100%.
     */
    // public void summaryTreeView_rowActivated_cb() {
    // TreePath cursorPath = _view.getCursorPath();
    // System.out.println("debug: treeviewactivated, " +
    // cursorPath.toString());
    // }
    public void activateSection(int index, boolean containsCurrentStep) {
        if ((index < 0) && (index >= _numSections)) {
            throw new DebugException(
                    "OverviewWindow's activateSection() was called with an illegal section number, "
                            + index);
        }

        /*
         * If we're now at the beginning, turn off the prev button
         */
        if (index == 0) {
            prev_toolbutton.setSensitive(false);
        } else {
            prev_toolbutton.setSensitive(true);
        }
        /*
         * If we're at now at the end, turn off the next button
         */
        if (index == (_numSections - 1)) {
            next_toolbutton.setSensitive(false);
        } else {
            next_toolbutton.setSensitive(true);
        }

        /*
         * If the current section contains the current step, then we don't
         * need the "Current" button hot; otherwise we do.
         */
        if (containsCurrentStep) {
            current_toolbutton.setSensitive(false);
        } else {
            current_toolbutton.setSensitive(true);
        }

        /*
         * Fairly straight forward - just scroll the TreeView to show the
         * active section.
         */
        TreePath tp = new TreePath(Integer.toString(index));

        view.getSelection().selectRow(tp);
        view.scrollToCell(tp, null);
    }
}
