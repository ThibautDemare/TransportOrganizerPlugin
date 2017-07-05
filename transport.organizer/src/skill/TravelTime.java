package skill;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

import msi.gama.runtime.IScope;

public class TravelTime implements NumberProvider {
	// temps de trajet -> le cout de traversé d'une arête correspond à :
	// la longueur * la vitesse du véhicule mais il faut aussi rajouter au temps de trajet
	// le temps de manutention au sein des nœuds+ éventuellement le temps d'attente du départ du véhicule
	private final IScope scope;
	
	public TravelTime(final IScope scope){
		this.scope = scope;
	}
	
	public double getEdgeCost(Node source, Edge e, Node dest) {
		double res = 0;
		// first, we add the time to cross the edge
		res += e.getNumber("length") * e.getNumber("speed");
		// second, get the time to leave the source node and to enter the edge
		if(source.hasAttribute("handling_time_to_"+e.getAttribute("graph_type"))){
			// We first get the handling time
			double handlingTime = source.getNumber("handling_time_to_"+e.getAttribute("graph_type"));
			
//TODO
//			// Then, and based on this handling time and the frequencies of vehicle departure, we determine when the goods can leave.
//			final IAgent agent = getCurrentAgent(scope);
//			IAgent transporter = (IAgent) agent.getAttribute("transporter_"+e.getAttribute("graph_type"));
//			double timeBeforeDeparture = 0;

			res += handlingTime;
		}
		// third, get the handling time to leave the edge and to enter the dest node
		if(dest.hasAttribute("handling_time_from_"+e.getAttribute("graph_type"))){
			res += source.getNumber("handling_time_from_"+e.getAttribute("graph_type"));
		}
		return res;
	}
}