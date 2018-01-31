package skill;

import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalUnit;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

import msi.gama.metamodel.agent.IAgent;
import msi.gama.runtime.IScope;
import msi.gama.util.GamaDate;

public class TravelTime implements NumberProvider {
	// temps de trajet -> le cout de traversé d'une arête correspond à :
	// la longueur * la vitesse du véhicule mais il faut aussi rajouter au temps de trajet
	// le temps de manutention au sein des nœuds + éventuellement le temps d'attente du départ du véhicule
	private final IScope scope;
	private final TransportOrganizerSkill tos;

	public TravelTime(final TransportOrganizerSkill tos, final IScope scope){
		this.tos = tos;
		this.scope = scope;
	}

	public double getEdgeCost(Node source, Edge e, Node dest, double currentDistance, double volume, Node previousMultiModalNode) {
		double res = 0;
		// first, we add the time to cross the edge
		if(e.hasAttribute("length") && e.hasAttribute("speed")){
			res += e.getNumber("length") / e.getNumber("speed");
		}
		else {
			// Default edge cost if length and speed have not been declared
			res += Math.hypot(source.getNumber("x")-dest.getNumber("x"), source.getNumber("y")-dest.getNumber("y"))/1000 / 50;
		}

		// second, get the handling time to enter the multi-modal node
		if(dest.hasAttribute("handling_time_from_"+e.getAttribute("graph_type"))){
			// We first get the handling time
			double handlingTime = (double)((IAgent)dest.getAttribute("gama_agent")).getAttribute("handling_time_from_"+e.getAttribute("graph_type"));
			res += handlingTime;

			// Then, we need to know when the next available vehicle leaves the previous multimodal node
			// To do so, we need to get the transporter on the next edge
			String graphType = e.getAttribute("graph_type");
			final IAgent transporter;
			// Here, I get the transporter agent.
			transporter = tos.getTransporter(scope, graphType);
			handlingTime = (double)((IAgent)previousMultiModalNode.getAttribute("gama_agent")).getAttribute("handling_time_to_"+e.getAttribute("graph_type"));
			GamaDate currentDate = scope.getClock().getCurrentDate().plusMillis(currentDistance*3600*1000);
			GamaDate minimalDepartureDate = currentDate.plusMillis(handlingTime*3600*1000);
			// But this object is not an instance of TransporterSkill, therefore, I need to use a static method with the agent in parameter
			GamaDate departure = TransporterSkill.getDepartureDate(scope, transporter, previousMultiModalNode, dest, minimalDepartureDate, volume);
			if(departure.getSecond() != 0){
				departure.plus(60-departure.getSecond(), ChronoUnit.SECONDS);
			}
			if(departure.getMinute() != 0 ){
				departure.plus(60-departure.getMinute(), ChronoUnit.MINUTES);
			}
			// Now, I know the departure date. I compute the time between now and the departure, and convert it in hours
			double hours = currentDate.until(departure, ChronoUnit.HOURS);
			res += hours;
		}
//		if(source.hasAttribute("handling_time_to_"+e.getAttribute("graph_type"))){
//			// We first get the handling time
//			double handlingTime = (double)((IAgent)dest.getAttribute("gama_agent")).getAttribute("handling_time_to_"+e.getAttribute("graph_type"));
//			res += handlingTime;
//		}
		return res;
	}
}