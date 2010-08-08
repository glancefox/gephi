/*
Copyright 2008-2010 Gephi
Authors : Eduardo Ramos <eduramiba@gmail.com>
Website : http://www.gephi.org

This file is part of Gephi.

Gephi is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Gephi is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Gephi.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gephi.datalaboratory.impl.manipulators.generalactions.ui;

import com.csvreader.CsvReader;
import java.awt.Component;
import java.awt.Dialog;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import javax.swing.JComponent;
import org.gephi.data.attributes.api.AttributeColumn;
import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeTable;
import org.gephi.data.attributes.api.AttributeType;
import org.gephi.datalaboratory.api.AttributeColumnsController;
import org.gephi.datalaboratory.api.DataTablesController;
import org.gephi.datalaboratory.api.GraphElementsController;
import org.gephi.graph.api.Attributes;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.Node;
import org.openide.DialogDisplayer;
import org.openide.WizardDescriptor;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.lookup.ServiceProvider;

// An example action demonstrating how the wizard could be called from within
// your code. You can copy-paste the code below wherever you need.
@ServiceProvider(service = ImportCSVUIWizardAction.class)
public final class ImportCSVUIWizardAction extends CallableSystemAction {

    public enum Mode {

        NODES_TABLE,
        EDGES_TABLE
    }
    private WizardDescriptor.Panel[] panels;
    private ImportCSVUIWizardPanel1 step1;
    private ImportCSVUIWizardPanel2 step2;
    private WizardDescriptor wizardDescriptor;

    public void performAction() {
        wizardDescriptor = new WizardDescriptor(getPanels());
        step1.setWizardDescriptor(wizardDescriptor);
        step2.setWizardDescriptor(wizardDescriptor);
        // {0} will be replaced by WizardDesriptor.Panel.getComponent().getName()
        wizardDescriptor.setTitleFormat(new MessageFormat("{0}"));
        wizardDescriptor.setTitle(getName());
        Dialog dialog = DialogDisplayer.getDefault().createDialog(wizardDescriptor);
        dialog.setVisible(true);
        dialog.toFront();
        boolean cancelled = wizardDescriptor.getValue() != WizardDescriptor.FINISH_OPTION;
        if (!cancelled) {
            switch ((Mode) wizardDescriptor.getProperty("mode")) {
                case NODES_TABLE:
                    importToNodesTable();
                    break;
                case EDGES_TABLE:
                    importToEdgesTable();
                    break;
            }
            Lookup.getDefault().lookup(DataTablesController.class).refreshCurrentTable();
        }
    }

    /**
     * Method for importing csv data to nodes table.
     * Only special case of treating columns is id columns.
     */
    private void importToNodesTable() {
        File file = (File) wizardDescriptor.getProperty("file");
        Character separator = (Character) wizardDescriptor.getProperty("separator");
        Charset charset = (Charset) wizardDescriptor.getProperty("charset");
        String[] columnNames = (String[]) wizardDescriptor.getProperty("columns-names");
        AttributeType[] columnTypes = (AttributeType[]) wizardDescriptor.getProperty("columns-types");
        Boolean assignNewNodeIds = (Boolean) wizardDescriptor.getProperty("assign-new-node-ids");

        if (columnNames.length == 0) {
            return;
        }

        try {
            //Prepare attribute columns for the column names, creating the not already existing column:
            AttributeColumnsController ac = Lookup.getDefault().lookup(AttributeColumnsController.class);
            AttributeTable nodesTable = Lookup.getDefault().lookup(AttributeController.class).getModel().getNodeTable();
            String idColumn = null;
            ArrayList<AttributeColumn> columnsList = new ArrayList<AttributeColumn>();
            for (int i = 0; i < columnNames.length; i++) {
                //Separate first id column found from the list to use as id. If more are found later, the will not be in the list and be ignored.
                if (columnNames[i].equalsIgnoreCase("id")) {
                    if (idColumn == null) {
                        idColumn = columnNames[i];
                    }
                } else if (nodesTable.hasColumn(columnNames[i])) {
                    columnsList.add(nodesTable.getColumn(columnNames[i]));
                } else {
                    columnsList.add(ac.addAttributeColumn(nodesTable, columnNames[i], columnTypes[i]));
                }
            }

            //Create nodes:
            GraphElementsController gec = Lookup.getDefault().lookup(GraphElementsController.class);
            Graph graph = Lookup.getDefault().lookup(GraphController.class).getModel().getGraph();
            String id = null;
            Node node;
            Attributes nodeAttributes;
            CsvReader reader = new CsvReader(new FileInputStream(file), separator, charset);
            reader.readHeaders();
            while (reader.readRecord()) {
                //Prepare the correct node to assign the attributes:
                if (idColumn != null) {
                    id = reader.get(idColumn);
                    if (id == null || id.isEmpty()) {
                        node = gec.createNode(null);//id null or empty, assign one
                    } else {
                        node = graph.getNode(id);
                        if (node != null) {//Node with that id already in graph
                            if (assignNewNodeIds) {
                                node = gec.createNode(null);
                            }
                        } else {
                            node = gec.createNode(null, id);//New id in the graph
                        }
                    }
                } else {
                    node = gec.createNode(null);
                }
                //Assign attributes to the current node:
                nodeAttributes = node.getNodeData().getAttributes();
                for (AttributeColumn column : columnsList) {
                    ac.setAttributeValue(reader.get(column.getTitle()), nodeAttributes, column);
                }
            }
        } catch (FileNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    /**
     * Method for importing csv data to edges table.
     * Special cases are id, source, target and type columns.
     */
    private void importToEdgesTable() {
        File file = (File) wizardDescriptor.getProperty("file");
        Character separator = (Character) wizardDescriptor.getProperty("separator");
        Charset charset = (Charset) wizardDescriptor.getProperty("charset");
        String[] columnNames = (String[]) wizardDescriptor.getProperty("columns-names");
        AttributeType[] columnTypes = (AttributeType[]) wizardDescriptor.getProperty("columns-types");
        Boolean createNewNodes = (Boolean) wizardDescriptor.getProperty("create-new-nodes");

        try {
            //Prepare attribute columns for the column names, creating the not already existing column:
            AttributeColumnsController ac = Lookup.getDefault().lookup(AttributeColumnsController.class);
            AttributeTable edges = Lookup.getDefault().lookup(AttributeController.class).getModel().getEdgeTable();
            String idColumn = null;
            String sourceColumn = null;
            String targetColumn = null;
            String typeColumn = null;
            ArrayList<AttributeColumn> columnsList = new ArrayList<AttributeColumn>();
            for (int i = 0; i < columnNames.length; i++) {
                //Separate first id column found from the list to use as id. If more are found later, the will not be in the list and be ignored.
                if (columnNames[i].equalsIgnoreCase("id")) {
                    if (idColumn == null) {
                        idColumn = columnNames[i];
                    }
                } else if (columnNames[i].equalsIgnoreCase("source") && sourceColumn == null) {//Separate first source column found from the list to use as source node id
                    sourceColumn = columnNames[i];
                } else if (columnNames[i].equalsIgnoreCase("target") && targetColumn == null) {//Separate first target column found from the list to use as target node id
                    targetColumn = columnNames[i];
                } else if (columnNames[i].equalsIgnoreCase("type") && typeColumn == null) {//Separate first type column found from the list to use as edge type (directed/undirected)
                    typeColumn = columnNames[i];
                } else if (edges.hasColumn(columnNames[i])) {
                    columnsList.add(edges.getColumn(columnNames[i]));
                } else {
                    columnsList.add(ac.addAttributeColumn(edges, columnNames[i], columnTypes[i]));
                }
            }

            //Create edges:
            GraphElementsController gec = Lookup.getDefault().lookup(GraphElementsController.class);
            Graph graph = Lookup.getDefault().lookup(GraphController.class).getModel().getGraph();
            String id = null;
            Edge edge;
            String sourceId, targetId;
            Node source, target;
            String type;
            boolean directed;
            Attributes edgeAttributes;
            CsvReader reader = new CsvReader(new FileInputStream(file), separator, charset);
            reader.readHeaders();
            while (reader.readRecord()) {
                sourceId=reader.get(sourceColumn);
                targetId=reader.get(targetColumn);

                if(sourceId==null||sourceId.isEmpty()||targetId==null||targetId.isEmpty()){
                    continue;//No correct source and target ids were provided, ignore row
                }

                source = graph.getNode(sourceId);
                target = graph.getNode(targetId);

                if ((source == null || target == null) && !createNewNodes) {//Don't create new nodes when they don't exist already
                    continue;//Ignore this edge row, since no new nodes should be created.
                } else {//Create new nodes when they don't exist already
                    if (source == null) {
                        source = gec.createNode(null,sourceId);
                    }
                    if (target == null) {
                        target = gec.createNode(null,targetId);
                    }
                }

                if (typeColumn != null) {
                    type = reader.get(typeColumn);
                    //Undirected if indicated correctly, otherwise always directed:
                    if (type != null) {
                        directed = !type.equalsIgnoreCase("undirected");
                    } else {
                        directed = true;
                    }
                } else {
                    directed = true;//Directed by default when no indicated
                }

                //Prepare the correct edge to assign the attributes:
                if (idColumn != null) {
                    id = reader.get(idColumn);
                    if (id == null || id.isEmpty()) {
                        edge = gec.createEdge(source, target, directed);//id null or empty, assign one
                    } else {
                        edge = gec.createEdge(id, source, target, directed);
                        if (edge == null) {//Edge with that id already in graph
                            edge = gec.createEdge(source, target, directed);
                        }
                    }
                } else {
                    edge = gec.createEdge(source, target, directed);
                }

                if (edge != null) {//Edge could be created because it does not already exist:
                    //Assign attributes to the current edge:
                    edgeAttributes = edge.getEdgeData().getAttributes();
                    for (AttributeColumn column : columnsList) {
                        ac.setAttributeValue(reader.get(column.getTitle()), edgeAttributes, column);
                    }
                }
            }
        } catch (FileNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    /**
     * Initialize panels representing individual wizard's steps and sets
     * various properties for them influencing wizard appearance.
     */
    private WizardDescriptor.Panel[] getPanels() {
        if (panels == null) {
            panels = new WizardDescriptor.Panel[]{
                        step1 = new ImportCSVUIWizardPanel1(),
                        step2 = new ImportCSVUIWizardPanel2()
                    };
            String[] steps = new String[panels.length];


            for (int i = 0; i
                    < panels.length; i++) {
                Component c = panels[i].getComponent();
                // Default step name to component name of panel. Mainly useful
                // for getting the name of the target chooser to appear in the
                // list of steps.
                steps[i] = c.getName();


                if (c instanceof JComponent) { // assume Swing components
                    JComponent jc = (JComponent) c;
                    // Sets step number of a component
                    // TODO if using org.openide.dialogs >= 7.8, can use WizardDescriptor.PROP_*:
                    jc.putClientProperty("WizardPanel_contentSelectedIndex", new Integer(i));
                    // Sets steps names for a panel
                    jc.putClientProperty("WizardPanel_contentData", steps);
                    // Turn on subtitle creation on each step
                    jc.putClientProperty("WizardPanel_autoWizardStyle", Boolean.TRUE);
                    // Show steps on the left side with the image on the background
                    jc.putClientProperty("WizardPanel_contentDisplayed", Boolean.TRUE);
                    // Turn on numbering of all steps
                    jc.putClientProperty("WizardPanel_contentNumbered", Boolean.TRUE);
                }
            }
        }
        return panels;
    }

    public String getName() {
        return NbBundle.getMessage(ImportCSVUIWizardAction.class, "ImportCSVUIWizardAction.name");
    }

    @Override
    public String iconResource() {
        return null;
    }

    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }
}
