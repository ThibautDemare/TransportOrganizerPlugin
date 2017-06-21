package skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.graphstream.graph.Edge;
import org.graphstream.graph.EdgeRejectedException;
import org.graphstream.graph.Graph;
import org.graphstream.graph.IdAlreadyInUseException;
import org.graphstream.graph.Node;
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
import msi.gama.util.IList;
import msi.gama.util.graph.GraphUtilsGraphStream;
import msi.gama.util.graph.IGraph;
import msi.gama.util.graph._Edge;
import msi.gama.util.graph._Vertex;
import msi.gaml.operators.Cast;
import msi.gaml.skills.Skill;
import msi.gaml.types.IType;
import skill.Dijkstra.NumberProvider;

@doc("This skill is intended to manage a multi-modal network. It means create it and compute shortest paths on it.")
@skill(name = IKeywordTOAdditional.TRANSPORT_ORGANIZER)
public class TransportOrganizerSkill extends Skill{

	private static Graph multiModalNetwork = null;
	private static HashMap<String, Dijkstra> dijkstras;

	private IGraph getGamaGraph(final IScope scope) {
		return (IGraph) scope.getArg(IKeywordTOAdditional.NETWORK, IType.GRAPH);
	}

	private String getMode(final IScope scope) {
		return (String) scope.getArg(IKeywordTOAdditional.MODE, IType.STRING);
	}

	private String getStrategy(final IScope scope) {
		return (String) scope.getArg(IKeywordTOAdditional.STRATEGY, IType.STRING);
	}

	private IList getMultiModalNodes(final IScope scope){
		return (IList) scope.getArg(IKeywordTOAdditional.MULTIMODALNODES, IType.LIST);
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
		name = "connect_networks",
		args = {
				@arg(name = IKeywordTOAdditional.MULTIMODALNODES, type = IType.LIST , optional = false, 
						doc = @doc("The list of nodes (agents) who connect the two networks.")),
				@arg(name = IKeywordTOAdditional.NETWORK1, type = IType.GRAPH , optional = false, 
						doc = @doc("The first network.")),
				@arg(name = IKeywordTOAdditional.NETWORK2, type = IType.GRAPH , optional = false, 
						doc = @doc("And the seconde one.")),
		},
		doc =
		@doc(value = "Connect two networks together with the multi-modal nodes.", examples = { @example("do connect_networks multi_modal_nodes:my_agents network_1:my_first_network network_2:my_second_network;") })
	)
	public void addMultiModalNodes(final IScope scope) throws GamaRuntimeException {
		IList multiModalNodes = getMultiModalNodes(scope);
		for(int i = 0; i<multiModalNodes.size(); i++){
			IAgent multiModalNode = (IAgent) multiModalNodes.get(i);

			// We first create a graphstream node corresponding to the current gama agent
			Node currentGSNode = multiModalNetwork.addNode(multiModalNode.toString());
			currentGSNode.addAttribute("gama_agent", multiModalNode);
			for ( Object key : multiModalNode.getAttributes().keySet() ) {
				Object value = GraphUtilsGraphStream.preprocessGamaValue(multiModalNode.getAttributes().get(key));
				if(value != null)
					currentGSNode.addAttribute(key.toString(), value.toString());
			}
			currentGSNode.setAttribute("x", multiModalNode.getLocation().getX());
			currentGSNode.setAttribute("y", multiModalNode.getLocation().getY()*-1);
			currentGSNode.setAttribute("z", multiModalNode.getLocation().getZ());

			// Find the closest nodes in the two networks
			Node node1 = getClosestGSNode(scope, (IGraph) scope.getArg(IKeywordTOAdditional.NETWORK1, IType.GRAPH), multiModalNode, currentGSNode);
			Node node2 = getClosestGSNode(scope, (IGraph) scope.getArg(IKeywordTOAdditional.NETWORK2, IType.GRAPH), multiModalNode, currentGSNode);
			// Create an edge between the current GS node and these two other nodes
			multiModalNetwork.addEdge(currentGSNode+"_"+node1, currentGSNode, node1);
			multiModalNetwork.addEdge(currentGSNode+"_"+node2, currentGSNode, node2);
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
}
