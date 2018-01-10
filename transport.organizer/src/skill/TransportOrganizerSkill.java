package skill;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.graphstream.graph.Edge;
import org.graphstream.graph.EdgeRejectedException;
import org.graphstream.graph.Graph;
import org.graphstream.graph.IdAlreadyInUseException;
import org.graphstream.graph.Node;
import org.graphstream.graph.Path;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.stream.file.FileSinkDGSFiltered;

import msi.gama.metamodel.agent.IAgent;
import msi.gama.metamodel.shape.IShape;
import msi.gama.metamodel.topology.ITopology;
import msi.gama.metamodel.topology.filter.In;
import msi.gama.metamodel.topology.graph.GraphTopology;
import msi.gama.precompiler.GamlAnnotations.action;
import msi.gama.precompiler.GamlAnnotations.arg;
import msi.gama.precompiler.GamlAnnotations.doc;
import msi.gama.precompiler.GamlAnnotations.example;
import msi.gama.precompiler.GamlAnnotations.skill;
import msi.gama.runtime.GAMA;
import msi.gama.runtime.IScope;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gama.util.GamaDate;
import msi.gama.util.GamaListFactory;
import msi.gama.util.IList;
import msi.gama.util.graph.GraphUtilsGraphStream;
import msi.gama.util.graph.IGraph;
import msi.gama.util.graph._Edge;
import msi.gama.util.graph._Vertex;
import msi.gaml.operators.Cast;
import msi.gaml.skills.Skill;
import msi.gaml.types.IType;
import skill.NumberProvider;

@doc("This skill is intended to manage a multi-modal network. It means create it and compute shortest paths on it.")
@skill(name = IKeywordTOAdditional.TRANSPORT_ORGANIZER)
public class TransportOrganizerSkill extends Skill{

	/*
	 * Attributes
	 */

	private static Graph multiModalNetwork = null;
	private static FileSinkDGSFiltered fileSink = null;
	private static HashMap<String, DijkstraComplexLength> dijkstras;

	/*
	 * Static methods
	 */

	public IAgent getTransporter(final IScope scope, String graphType){
		return (IAgent) getCurrentAgent(scope).getAttribute("transporter_"+graphType);
	}

	private static IGraph getGamaGraph(final IScope scope) {
		return (IGraph) scope.getArg(IKeywordTOAdditional.NETWORK, IType.GRAPH);
	}

	private static String getMode(final IScope scope) {
		return (String) scope.getArg(IKeywordTOAdditional.MODE, IType.STRING);
	}

	private static String getStrategy(final IScope scope) {
		return (String) scope.getArg(IKeywordTOAdditional.STRATEGY, IType.STRING);
	}

	private static IList getNodes(final IScope scope){
		return (IList) scope.getArg(IKeywordTOAdditional.NODES, IType.LIST);
	}

	/*
	 * Tools
	 */

	private void flush() {
		try {
			fileSink.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Return the closest Graphstream node
	 * @param scope
	 * @param network
	 * @param gamaNode
	 * @param gsNode
	 * @return
	 */
	private Node getClosestGSNode(final IScope scope, IGraph network, IAgent gamaNode, Node gsNode){
		GraphTopology gt = (GraphTopology)(Cast.asTopology(scope, network));
		ITopology topo = scope.getSimulation().getTopology();
		IAgent closestEdge = topo.getAgentClosestTo(scope, gamaNode, In.edgesOf(gt.getPlaces()));
		Edge gsClosestEdge = (Edge)closestEdge.getAttribute("graphstream_edge_"+gsNode.getGraph().getId());
		Node closestNode1 = gsClosestEdge.getNode0();
		Node closestNode2 = gsClosestEdge.getNode1();
		if(Math.hypot(gsNode.getNumber("x")-closestNode1.getNumber("x"), gsNode.getNumber("y")-closestNode1.getNumber("y")) <
				Math.hypot(gsNode.getNumber("x")-closestNode2.getNumber("x"), gsNode.getNumber("y")-closestNode2.getNumber("y")) ){
			return closestNode1;
		}
		return closestNode2;
	}

	private void initFileSink(){
		// We save the graph in a DGS file (mostly for debug purpose but can be use for something else too)
		fileSink = new FileSinkDGSFiltered();
		multiModalNetwork.addSink(fileSink);
		try {
			String fileName = new File(new File("."), "../workspace-model/DALSim/results/DGS/multiModalNetwork.dgs").getCanonicalPath();
			fileSink.begin(fileName);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Filter useless attributes in edges
		fileSink.addEdgeAttributeFiltered("gama_agent");
		fileSink.addEdgeAttributeFiltered("color");
		fileSink.addEdgeAttributeFiltered("graphstream_edge");
		fileSink.addEdgeAttributeFiltered("gama_time");
		fileSink.addEdgeAttributeFiltered("gamaGraph");
		fileSink.addEdgeAttributeFiltered("flagAttribute");
		fileSink.addEdgeAttributeFiltered("Shape_Leng");
		fileSink.addEdgeAttributeFiltered("cout_�");
		fileSink.addEdgeAttributeFiltered("sur_gasoil");
		fileSink.addEdgeAttributeFiltered("CO2");
		fileSink.addEdgeAttributeFiltered("cout");
		fileSink.addEdgeAttributeFiltered("dur�e_min");
		fileSink.addEdgeAttributeFiltered("Long_km");
		fileSink.addEdgeAttributeFiltered("RTT");
		fileSink.addEdgeAttributeFiltered("RTN");
		fileSink.addEdgeAttributeFiltered("RTE");
		fileSink.addEdgeAttributeFiltered("RSU");
		fileSink.addEdgeAttributeFiltered("RST");
		fileSink.addEdgeAttributeFiltered("MED");
		fileSink.addEdgeAttributeFiltered("LOC");
		fileSink.addEdgeAttributeFiltered("EXS");
		fileSink.addEdgeAttributeFiltered("SN");
		fileSink.addEdgeAttributeFiltered("ICC");
		fileSink.addEdgeAttributeFiltered("F_CODE");
		fileSink.addEdgeAttributeFiltered("gfid");
		fileSink.addEdgeAttributeFiltered("FCsubtype");
		fileSink.addEdgeAttributeFiltered("OBJECTID_1");
		fileSink.addEdgeAttributeFiltered("OBJECTID");

		// Filter useless attributes in nodes
		fileSink.addNodeAttributeFiltered("dijkstra_travel_time");
		fileSink.addNodeAttributeFiltered("flagAttribute");
		fileSink.addNodeAttributeFiltered("graphstream_node");
		fileSink.addNodeAttributeFiltered("gamaGraph");
		fileSink.addNodeAttributeFiltered("totalSurface");
		fileSink.addNodeAttributeFiltered("probaAnt");
		fileSink.addNodeAttributeFiltered("surface");
		fileSink.addNodeAttributeFiltered("gama_agent");
		fileSink.addNodeAttributeFiltered("id");
		fileSink.addNodeAttributeFiltered("speed");
		fileSink.addNodeAttributeFiltered("Id");
		fileSink.addNodeAttributeFiltered("Nom");
		fileSink.addNodeAttributeFiltered("Activit�");
		fileSink.addNodeAttributeFiltered("co_ferro");
		fileSink.addNodeAttributeFiltered("co_auto");
		fileSink.addNodeAttributeFiltered("surf_stock");
		fileSink.addNodeAttributeFiltered("cap_EVP_an");
		fileSink.addNodeAttributeFiltered("INDEX_NO");
		fileSink.addNodeAttributeFiltered("PORT_NAME");
		fileSink.addNodeAttributeFiltered("TERMINAL_N");
		fileSink.addNodeAttributeFiltered("COUNTRY");
		fileSink.addNodeAttributeFiltered("LATITUDE");
		fileSink.addNodeAttributeFiltered("LONGITUDE");
		fileSink.addNodeAttributeFiltered("Date_de_MA");
		fileSink.addNodeAttributeFiltered("TOC");

		// No need to save graph attributes
		fileSink.setNoFilterGraphAttributeAdded(false);
		fileSink.setNoFilterGraphAttributeChanged(false);
		fileSink.setNoFilterGraphAttributeRemoved(false);

		// and no need either of result which contains Dijsktra reference
		fileSink.addNodeAttributeFiltered("dijkstra_travel_time");
		fileSink.addNodeAttributeFiltered("dijkstra_financial_costs");
	}

	/**
	 * Takes a gama graph as an input, returns a graphstream graph as
	 * close as possible. Preserves double links (multi graph).
	 * Copy of the method of GraphUtilsGraphStream but we save the gama agent in each edges/nodes and the graphstream edge in each gama edge agent
	 * @param gamaGraph
	 * @return The Graphstream graph
	 */
	private void convertGamaGraphToGraphstreamGraph(IScope scope, final IGraph gamaGraph, Graph g, String graphType) {
		Map<Object, Node> gamaNode2graphStreamNode = new HashMap<Object, Node>(gamaGraph._internalNodesSet().size());

		// The GS graph keeps the list of the gama graph used in order to 
		if(g.hasAttribute("listGamaGraph")){
			((ArrayList<IGraph>)g.getAttribute("listGamaGraph")).add(gamaGraph);
		}
		else {
			ArrayList<IGraph> l = new ArrayList<IGraph>();
			l.add(gamaGraph);
			g.addAttribute("listGamaGraph", l);
		}

		// add nodes
		for ( Object v : gamaGraph._internalVertexMap().keySet() ) {
			_Vertex vertex = (_Vertex) gamaGraph._internalVertexMap().get(v);
			Node n = g.addNode(v.toString());
			gamaNode2graphStreamNode.put(v, n);
			n.addAttribute("gamaGraph", gamaGraph);
			n.addAttribute("graph_type", graphType);
			if ( v instanceof IAgent ) {
				IAgent a = (IAgent) v;
				n.addAttribute("gama_agent", a);
				for ( Object key : a.getAttributes().keySet() ) {
					Object value = GraphUtilsGraphStream.preprocessGamaValue(a.getAttributes().get(key));
					if(value != null)
						n.addAttribute(key.toString(), value.toString());
				}
			}

			if ( v instanceof IShape ) {
				IShape sh = (IShape) v;
				n.setAttribute("x", sh.getLocation().getX());
				n.setAttribute("y", sh.getLocation().getY()*-1);
				n.setAttribute("z", sh.getLocation().getZ());
			}
		}

		flush();

		// add edges
		for ( Object edgeObj : gamaGraph._internalEdgeMap().keySet() ) {
			_Edge edge = (_Edge) gamaGraph._internalEdgeMap().get(edgeObj);
			try {
				Edge e = // We call the function where we give the nodes object directly, is it more efficient than give the string id? Because, if no, we don't need the "gamaNode2graphStreamNode" map...
						g.addEdge(edgeObj.toString(), gamaNode2graphStreamNode.get(edge.getSource()), gamaNode2graphStreamNode.get(edge.getTarget()),
								gamaGraph.isDirected() );// till now, directionality of an edge depends on the whole gama graph
				if ( edgeObj instanceof IAgent ) {
					IAgent a = (IAgent) edgeObj;
					// e know a
					e.addAttribute("gama_agent", a);
					e.addAttribute("gamaGraph", gamaGraph);
					e.addAttribute("graph_type", graphType);
					for ( Object key : a.getAttributes().keySet() ) {
						Object value = GraphUtilsGraphStream.preprocessGamaValue(a.getAttributes().get(key));
						if(value != null)
							e.addAttribute(key.toString(), value.toString());
					}
					//e.addAttribute("gama_time", e.getNumber(length_attribute) * e.getNumber(speed_attribute));
					// a know e
					a.setAttribute("graphstream_edge_"+g.getId(), e);
				}
			} catch (EdgeRejectedException e) {
				((IAgent) edgeObj).setAttribute("col", "red");
				//g.getEdge(edgeObj.toString()).addAttribute("ui.style", "fill-color:red");
				GAMA.reportError(scope, GamaRuntimeException
						.warning("an edge was rejected during the transformation, probably because it was a double one => id : "+((IAgent) edgeObj).getName(), scope),
						true);
			} catch (IdAlreadyInUseException e) {
				((IAgent) edgeObj).setAttribute("col", "o");
				GAMA.reportError(scope, GamaRuntimeException
						.warning("an edge was rejected during the transformation, probably because it was a double one => id : "+((IAgent) edgeObj).getName(), scope),
						true);
			}
		}

		flush();
	}

	/*
	 * Main methods
	 */

	@action(
		name = "add_network",
		args = {
				@arg(name = IKeywordTOAdditional.NETWORK, type = IType.GRAPH , optional = false, doc = @doc("the GAMA graph which needs to be added.")),
				@arg(name = IKeywordTOAdditional.MODE, type = IType.STRING , optional = false, doc = @doc("Which mode of transport is this network (road, maritime,...).")),
				@arg(name = IKeywordTOAdditional.NODES, type = IType.LIST , optional = false, doc = @doc("The list of multi modal nodes which are connected to this network."))
		},
		doc =
		@doc(value = "Add a network to the multi-modal one. You need to add first the road network.", examples = { @example("do add_network network:my_network mode:'road' nodes:my_nodes;") })
	)
	public void addMode(final IScope scope) throws GamaRuntimeException {
		IList nodes = (IList) scope.getArg(IKeywordTOAdditional.NODES, IType.LIST);
		String mode = (String) scope.getArg(IKeywordTOAdditional.MODE, IType.STRING);
		if(getMode(scope).equals("road")){
			/*
			Si réseau routier passé en param :
				on crée l'objet multiModalNetwork
				on converti le graphe GAMA et on met tout ça dans multiModalNetwork
				on connecte en plus les noeuds passés en param à ce graph
			*/
			multiModalNetwork = new MultiGraph("main", false, false);
			dijkstras = new HashMap<String, DijkstraComplexLength>();
			multiModalNetwork.display(false);
			initFileSink();
			convertGamaGraphToGraphstreamGraph(scope, getGamaGraph(scope), multiModalNetwork, getMode(scope));
			createGSNode(nodes, multiModalNetwork, "main");
			connectNodesToNetwork(scope, nodes, getGamaGraph(scope), "main");
		}
		else {
			/*
			Sinon
				on crée un nouveau graph dans lequel on met le graphe GAMA converti
				on connecte en plus les noeuds passés en param à ce graphe
				on stock ce graph dans les nœeuds passé en param
				on ajoute les nœeuds passés en param à multiModalNetwork
				on connecte entre eux les nœuds passés en param
			*/
			Graph subnetwork = new MultiGraph(mode, true, false);
			subnetwork.display(false);
			convertGamaGraphToGraphstreamGraph(scope, getGamaGraph(scope), subnetwork, getMode(scope));
			createGSNode(nodes, subnetwork, mode);
			connectNodesToNetwork(scope, nodes, getGamaGraph(scope), mode);
			createGSNode(nodes, multiModalNetwork, "main");
			connectMultimodalNodesDirectly(scope, nodes);
		}
		flush();
		scope.getSimulation().setAttribute("multiModalNetwork", multiModalNetwork);
	}

	private void createGSNode(IList nodes, Graph graph, String mode){
		for(int i = 0; i<nodes.size(); i++){
			IAgent multiModalNode = (IAgent) nodes.get(i);
			// We create (if needed) a graphstream node corresponding to the current gama agent
			Node currentGSNode = (Node) multiModalNode.getAttribute("graphstream_node_"+mode);
			if(currentGSNode == null){
				currentGSNode = graph.addNode(multiModalNode.toString());
				currentGSNode.addAttribute("gama_agent", multiModalNode);
				currentGSNode.addAttribute("multiModalNode", true);
				multiModalNode.setAttribute("graphstream_node_"+mode, currentGSNode);
				for ( Object key : multiModalNode.getAttributes().keySet() ) {
					Object value = GraphUtilsGraphStream.preprocessGamaValue(multiModalNode.getAttributes().get(key));
					if(value != null)
						currentGSNode.addAttribute(key.toString(), value.toString());
				}
				currentGSNode.setAttribute("x", multiModalNode.getLocation().getX());
				currentGSNode.setAttribute("y", multiModalNode.getLocation().getY()*-1);
				currentGSNode.setAttribute("z", multiModalNode.getLocation().getZ());
			}
		}
	}

	private void connectNodesToNetwork(IScope scope, IList nodes, IGraph network, String mode) {
		for(int i = 0; i<nodes.size(); i++){
			IAgent multiModalNode = (IAgent) nodes.get(i);
			// We get the graphstream node corresponding to the current gama agent
			Node currentGSNode = (Node) multiModalNode.getAttribute("graphstream_node_"+mode);
			// Then we connect this new node to the network
			// Find the closest nodes in this network
			Node node = getClosestGSNode(scope, network, multiModalNode, currentGSNode);
			// Create an edge between the current GS node and these two other nodes
			Edge e = currentGSNode.getGraph().addEdge(currentGSNode+"_"+node, currentGSNode, node);
			e.addAttribute("length", Math.hypot(currentGSNode.getNumber("x")-node.getNumber("x"), currentGSNode.getNumber("y")-node.getNumber("y")) / 1000); // Hypot returns a measure in meter. I want km.
			e.addAttribute("speed", 10);
			e.addAttribute("graph_type", ""+node.getAttribute("graph_type"));
		}
		flush();
	}

	private void connectMultimodalNodesDirectly(IScope scope, IList nodes) {
		// We give different color for the different modes of transport
		Random random = new Random();
		final float hue = random.nextFloat();
		final float saturation = 0.9f;//1.0 for brilliant, 0.0 for dull
		final float luminance = 1.0f; //1.0 for brighter, 0.0 for black
		Color color = Color.getHSBColor(hue, saturation, luminance);
		// We connect each multi modal node to the other ones
		for(int i = 0; i<nodes.size(); i++){
			IAgent gamaAgent1 = (IAgent) nodes.get(i);
			// We get the graphstream node corresponding to the current gama agent
			Node gsNode1 = multiModalNetwork.getNode(gamaAgent1.toString());
			for(int j = 0; j<nodes.size(); j++){
				IAgent gamaAgent2 = (IAgent) nodes.get(j);
				// We get the graphstream node corresponding to the current gama agent
				Node gsNode2 = multiModalNetwork.getNode(gamaAgent2.toString());
				Edge e = multiModalNetwork.addEdge(gsNode1.getId()+"_"+gsNode2.getId(), gsNode1, gsNode2);
				e.addAttribute("ui.style", "fill-color: rgb("+color.getRed()+","+color.getGreen()+","+color.getBlue()+");");
			}
		}
		flush();
	}

	@action(
		name = "compute_shortest_path",
		args = {
				@arg(name = IKeywordTOAdditional.ORIGIN, type = IType.AGENT , optional = false, doc = @doc("the location or entity towards which to move.")),
				@arg(name = IKeywordTOAdditional.DESTINATION, type = IType.AGENT , optional = false, doc = @doc("the location or entity towards which to move.")),
				@arg(name = IKeywordTOAdditional.STRATEGY, type = IType.STRING , optional = false, doc = @doc("The strategy used by the agent to compute the shortest path. Among: 'travel_time' and 'financial_costs'.")),
				@arg(name = IKeywordTOAdditional.COMMODITY, type = IType.AGENT , optional = false, doc = @doc("the location or entity towards which to move.")),
		},
		doc =	@doc(value = "Compute a shortest path between two nodes.", returns = "the path with the list of multi-modal nodes.", 
					examples = { @example("do compute_shortest_path origin:my_origin_agent destination:my_destination_agent strategy:'travel_time' volume:150 returns:my_returned_path;") })
	)
	public IList computeShortestPath(final IScope scope) throws GamaRuntimeException {
		// First, we get the dijsktra we need (there is one dijsktra per strategy)
		DijkstraComplexLength dijkstra;
		String strategy = getStrategy(scope);
		if(dijkstras.containsKey("dijkstra_"+strategy)){
			dijkstra = dijkstras.get("dijkstra_"+strategy);
		}
		else{
			if(strategy.equals("travel_time")){
				dijkstra = new DijkstraComplexLength("dijkstra_"+strategy, new TravelTime(this, scope));
			}else{
				dijkstra = new DijkstraComplexLength("dijkstra_"+strategy, new FinancialCosts());
			}
			dijkstra.init(multiModalNetwork);
			dijkstras.put("dijkstra_"+strategy, dijkstra);
		}
		//Get the graphstream source and target node
		IAgent gamaSource = (IAgent) scope.getArg(IKeywordTOAdditional.ORIGIN, IType.AGENT);
		Node sourceNode = (Node) gamaSource.getAttribute("graphstream_node");
		IAgent gamaTarget = (IAgent) scope.getArg(IKeywordTOAdditional.DESTINATION, IType.AGENT);
		Node targetNode = (Node) gamaTarget.getAttribute("graphstream_node");
		// Compute and get the path
		dijkstra.setSource(sourceNode);
		dijkstra.compute((double)((IAgent) scope.getArg(IKeywordTOAdditional.COMMODITY, IType.AGENT)).getAttribute("volume"));
		flush();
		Path p = dijkstra.getPath(targetNode);
		// Construct the output list with intermodal nodes and networks between them
		IList path = GamaListFactory.create();
		boolean isNodeFound = false;
		Node foundNode = null;
		List<Node> nodes = p.getNodePath();
		for(int i = 0; i < nodes.size(); i++){
			Node n =nodes.get(i);
			n.addAttribute("ui.style", "fill-color:red;");
			if(isNodeFound){
				path.add(n.getAttribute("gamaGraph"));
				isNodeFound = false;
				if(i < nodes.size()-1){
					Node destination = null;
					int j = i + 1;
					while(j < nodes.size() && destination == null){
						if(nodes.get(j).hasAttribute("graphstream_node")){
							destination = nodes.get(j);
						}
						j++;
					}
					GamaDate departureDate = scope.getClock().getCurrentDate() // and when does it leave.
							.plusMillis((double)((IAgent)foundNode.getAttribute("gama_agent")).getAttribute("handling_time_to_"+n.getAttribute("graph_type"))*3600*1000)
							.plusMillis(dijkstra.getPathLength(foundNode)*3600*1000);
					if(departureDate.getSecond() != 0){
						departureDate.plus(60-departureDate.getSecond(), ChronoUnit.SECONDS);
					}
					if(departureDate.getMinute() != 0 ){
						departureDate.plus(60-departureDate.getMinute(), ChronoUnit.MINUTES);
					}
					TransporterSkill.registerDepartureDate(
						scope,
						getTransporter(scope, nodes.get(i).getAttribute("graph_type")),// The network on which the vehicle move
						(IAgent) scope.getArg(IKeywordTOAdditional.COMMODITY, IType.AGENT), // The goods to carry
						foundNode,// The node from which the vehicle move
						destination, // the destination of this vehicle
						departureDate
					);
				}
			}
			else if(n.hasAttribute("graphstream_node")){
				path.add(n.getAttribute("gama_agent"));
				foundNode = n;
				isNodeFound = true;
			}
		}
		return path;
		// retourne une liste contenant la suite des noeuds multi-modaux par lesquels il faut passer et entre chaque noeud, le graph qu'il faut emprunter
		
		// strategy:
		//			temps de trajet -> le cout de traversé d'une arête correspond à la longueur * la vitesse du véhicule mais il faut aussi rajouter au temps de trajet, le temps de manutention au sein des nœuds+ éventuellement le temps d'attente du départ du véhicule
		//			coût financier -> le cout de traversé d'une arête correspond à la longueur * volume de marchandise * cout km.volume du véhicule mais il faut aussi rajouter au temps de trajet, le temps de manutention au sein des nœuds
		//			moins cher à échéance
		//			coût carbone ?? => TODO one day
	
	}
}
