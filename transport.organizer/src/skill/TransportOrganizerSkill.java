package skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.graphstream.graph.Edge;
import org.graphstream.graph.EdgeRejectedException;
import org.graphstream.graph.Graph;
import org.graphstream.graph.IdAlreadyInUseException;
import org.graphstream.graph.Node;
import org.graphstream.graph.Path;
import org.graphstream.graph.implementations.MultiGraph;

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

	private static Graph multiModalNetwork = null;
	private static HashMap<String, DijkstraComplexLength> dijkstras;

	private IGraph getGamaGraph(final IScope scope) {
		return (IGraph) scope.getArg(IKeywordTOAdditional.NETWORK, IType.GRAPH);
	}

	private String getMode(final IScope scope) {
		return (String) scope.getArg(IKeywordTOAdditional.MODE, IType.STRING);
	}

	private String getStrategy(final IScope scope) {
		return (String) scope.getArg(IKeywordTOAdditional.STRATEGY, IType.STRING);
	}

	private IList getNodes(final IScope scope){
		return (IList) scope.getArg(IKeywordTOAdditional.NODES, IType.LIST);
	}

	@action(
		name = "add_network",
		args = {
				@arg(name = IKeywordTOAdditional.NETWORK, type = IType.GRAPH , optional = false, doc = @doc("the GAMA graph which needs to be added.")),
				@arg(name = IKeywordTOAdditional.MODE, type = IType.STRING , optional = false, doc = @doc("Which mode of transport is this network (road, maritime,...).")),
		},
		doc =
		@doc(value = "Add a network to the multi-modal one.", examples = { @example("do add_network network:my_network mode:'river';") })
	)
	public void addMode(final IScope scope) throws GamaRuntimeException {
		// Do I need to init the multiModalNetwork ?
		multiModalNetwork = (Graph) scope.getSimulation().getAttribute("multiModalNetwork");
		if(multiModalNetwork == null){
			multiModalNetwork = new MultiGraph("multiModalNetwork", true, false); // TODO : verifier les parametres car le strict checking devrait être supprimé non?
			multiModalNetwork.display(false);
		}
		// Convert the gama network to a graphstream graph
		convertGamaGraphToGraphstreamGraph(scope, getGamaGraph(scope), multiModalNetwork, getMode(scope));
		scope.getSimulation().setAttribute("multiModalNetwork", multiModalNetwork);
	}

	@action(
		name = "connect_nodes",
		args = {
				@arg(name = IKeywordTOAdditional.NODES, type = IType.LIST , optional = false, 
						doc = @doc("The list of nodes (agents) who connect the two networks.")),
				@arg(name = IKeywordTOAdditional.NETWORKS, type = IType.LIST , optional = false, 
						doc = @doc("The list of networks.")),
		},
		doc =
		@doc(value = "Connect some nodes to some networks.", examples = { @example("do connect_networks nodes:my_agents networks:my_networks;") })
	)
	public void addNodes(final IScope scope) throws GamaRuntimeException {
		IList nodes = getNodes(scope);
		for(int i = 0; i<nodes.size(); i++){
			IAgent multiModalNode = (IAgent) nodes.get(i);

			// We first create (if needed) a graphstream node corresponding to the current gama agent
			Node currentGSNode = (Node) multiModalNode.getAttribute("graphstream_node");
			if(currentGSNode == null){
				currentGSNode = multiModalNetwork.addNode(multiModalNode.toString());
				currentGSNode.addAttribute("gama_agent", multiModalNode);
				multiModalNode.setAttribute("graphstream_node", currentGSNode);
				for ( Object key : multiModalNode.getAttributes().keySet() ) {
					Object value = GraphUtilsGraphStream.preprocessGamaValue(multiModalNode.getAttributes().get(key));
					if(value != null)
						currentGSNode.addAttribute(key.toString(), value.toString());
				}
				currentGSNode.setAttribute("x", multiModalNode.getLocation().getX());
				currentGSNode.setAttribute("y", multiModalNode.getLocation().getY()*-1);
				currentGSNode.setAttribute("z", multiModalNode.getLocation().getZ());
			}

			// Then we connect this new node to the other networks
			IList listNetworks = (IList) scope.getArg(IKeywordTOAdditional.NETWORKS, IType.LIST);
			for(int j = 0; j < listNetworks.size(); j++){
				IGraph network = (IGraph) listNetworks.get(j);
				// Find the closest nodes in this network
				Node node = getClosestGSNode(scope, network, multiModalNode, currentGSNode);
				// Create an edge between the current GS node and these two other nodes
				multiModalNetwork.addEdge(currentGSNode+"_"+node, currentGSNode, node);
			}
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
		Edge gsClosestEdge = (Edge)closestEdge.getAttribute("graphstream_edge");
		Node closestNode1 = gsClosestEdge.getNode0();
		Node closestNode2 = gsClosestEdge.getNode1();
		if(Math.hypot(gsNode.getNumber("x")-closestNode1.getNumber("x"), gsNode.getNumber("y")-closestNode1.getNumber("y")) <
				Math.hypot(gsNode.getNumber("x")-closestNode2.getNumber("x"), gsNode.getNumber("y")-closestNode2.getNumber("y")) ){
			return closestNode1;
		}
		return closestNode2;
	}


	/**
	 * Takes a gama graph as an input, returns a graphstream graph as
	 * close as possible. Preserves double links (multi graph).
	 * Copy of the method of GraphUtilsGraphStream but we save the gama agent in each edges/nodes and the graphstream edge in each gama edge agent
	 * @param gamaGraph
	 * @return The Graphstream graph
	 */
	private static void convertGamaGraphToGraphstreamGraph(IScope scope, final IGraph gamaGraph, Graph g, String graphType) {
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
			if ( v instanceof IAgent ) {
				IAgent a = (IAgent) v;
				n.addAttribute("gama_agent", a);
				n.addAttribute("gamaGraph", gamaGraph);
				n.addAttribute("graph_type", graphType);
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
					a.setAttribute("graphstream_edge", e);
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
	}

	@action(
		name = "compute_shortest_path",
		args = {
				@arg(name = IKeywordTOAdditional.ORIGIN, type = IType.AGENT , optional = false, doc = @doc("the location or entity towards which to move.")),
				@arg(name = IKeywordTOAdditional.DESTINATION, type = IType.AGENT , optional = false, doc = @doc("the location or entity towards which to move.")),
				@arg(name = IKeywordTOAdditional.STRATEGY, type = IType.STRING , optional = false, doc = @doc("The strategy used by the agent to compute the shortest path. Among: 'travel_time' and 'financial_costs'.")),
				@arg(name = IKeywordTOAdditional.VOLUME, type = IType.FLOAT , optional = false, doc = @doc("the location or entity towards which to move.")),
		},
		doc =	@doc(value = "Compute a shortest path between two nodes.", returns = "the path with the list of multi-modal nodes.", 
					examples = { @example("do compute_shortest_path origin:my_origin_agent destination:my_destination_agent strategy:'travel_time' volume:150 returns:my_returned_path;") })
	)
	public IList computeShortestPath(final IScope scope) throws GamaRuntimeException {
		if(dijkstras == null){
			dijkstras = new HashMap<String, DijkstraComplexLength>();
		}

		// First, we get the dijsktra we need (there is one dijsktra per strategy)
		DijkstraComplexLength dijkstra;
		String strategy = getStrategy(scope);
		if(dijkstras.containsKey("dijkstra_"+strategy)){
			dijkstra = dijkstras.get("dijkstra_"+strategy);
		}
		else{
			if(strategy.equals("travel_time")){
				dijkstra = new DijkstraComplexLength("dijkstra_"+strategy, new TravelTime(scope));
			}else{
				dijkstra = new DijkstraComplexLength("dijkstra_"+strategy, new FinancialCosts());
			}
			dijkstra.init(multiModalNetwork);
			dijkstras.put("dijkstra_"+strategy, dijkstra);
		}

		//Get the graphstream source and target node
		IAgent gamaSource = (IAgent) scope.getArg(IKeywordTOAdditional.ORIGIN, IType.AGENT);
		System.out.println("gamaSource : "+gamaSource.getName());
		Node sourceNode = (Node) gamaSource.getAttribute("graphstream_node");
		System.out.println("source node : "+sourceNode);
		sourceNode.addAttribute("ui.style", "fill-color:blue;");
		IAgent gamaTarget = (IAgent) scope.getArg(IKeywordTOAdditional.DESTINATION, IType.AGENT);
		System.out.println("gamaTarget : "+gamaTarget.getName());
		Node targetNode = (Node) gamaTarget.getAttribute("graphstream_node");
		targetNode.addAttribute("ui.style", "fill-color:green;");
		System.out.println("target node : "+targetNode);
		// Compute and get the path
		dijkstra.setSource(sourceNode);
		dijkstra.compute();
		Path p = dijkstra.getPath(targetNode);
		System.out.println("p.getNodeCount() : "+p.getNodeCount());
		// Construct the output list
		IList path = GamaListFactory.create();
		for(Node n : p.getEachNode()){
			n.addAttribute("ui.style", "fill-color:red;");
			if(n.hasAttribute("graphstream_node"))
				path.add(n.getAttribute("gama_agent"));
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
