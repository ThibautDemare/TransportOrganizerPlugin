package skill;

import java.io.File;
import java.io.IOException;

import org.graphstream.algorithm.Toolkit;
import org.graphstream.graph.Edge;
import org.graphstream.graph.ElementNotFoundException;
import org.graphstream.graph.Node;
import org.graphstream.graph.Path;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.stream.GraphParseException;

import msi.gama.metamodel.agent.IAgent;
import msi.gama.util.GamaListFactory;
import msi.gama.util.IList;
import msi.gaml.types.IType;
import skill.NumberProvider;

public class Test {
	public static void main(String args[]) throws IOException, ElementNotFoundException, GraphParseException{
		MultiGraph multiModalNetwork = new MultiGraph("multiModalNetwork", true, false); // TODO : verifier les parametres car le strict checking devrait être supprimé non?
		multiModalNetwork.display(false);
		String fileName = new File(new File("."), "../../../workspace-model/DALSim/results/DGS/multiModalNetwork.dgs").getCanonicalPath();
		multiModalNetwork.read(fileName);
		for(Edge e : multiModalNetwork.getEachEdge())
			e.addAttribute("ui.style", "fill-color:black;");
		multiModalNetwork.getEdge("MaritimeLine2").addAttribute("ui.style", "fill-color:red;");
		SubDijkstra dijkstra = new SubDijkstra("travel_time", new TravelTime());
//		DijkstraComplexLength dijkstra = new DijkstraComplexLength("financial_costs", new FinancialCosts());
		dijkstra.init(multiModalNetwork);

		// Reset style
		for(Node n : multiModalNetwork.getEachNode()){
			if(n.hasAttribute("col"))
				n.addAttribute("ui.style", "size:3px;");
			else
				n.addAttribute("ui.style", "size:3px;fill-color:black;");
		}

		for(Edge n : multiModalNetwork.getEachEdge()){
			if(n.hasAttribute("col"))
				n.addAttribute("ui.style", "size:3px;fill-color:"+n.getAttribute("col")+";");
			else
				n.addAttribute("ui.style", "size:3px;fill-color:grey;");
		}

		//Get the graphstream source and target node
		Node sourceNode = multiModalNetwork.getNode("Provider0");//Toolkit.randomNode(multiModalNetwork);
		sourceNode.addAttribute("ui.style", "size:3px;fill-color:blue;");
		Node targetNode = Toolkit.randomNode(multiModalNetwork);//multiModalNetwork.getNode("Warehouse2782");//
		targetNode.addAttribute("ui.style", "size:3px;fill-color:green;");

		// Compute and get the path
		dijkstra.setSource(sourceNode);
		dijkstra.compute();
		Path p = dijkstra.getPath(targetNode);
		// Construct the output list
		for(Node n : p.getEachNode()){
			n.addAttribute("ui.style", "fill-color:red;");
		}
		for(Edge n : p.getEachEdge()){
			n.addAttribute("ui.style", "fill-color:red;");
		}
	}
}
