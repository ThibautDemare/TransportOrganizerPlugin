package skill;

import java.time.temporal.ChronoUnit;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

import msi.gama.metamodel.agent.IAgent;
import msi.gama.runtime.IScope;
import msi.gama.util.GamaDate;

public abstract class NumberProvider {
	protected final IScope scope;
	protected final TransportOrganizerSkill tos;

	public NumberProvider(final TransportOrganizerSkill tos, final IScope scope){
		this.tos = tos;
		this.scope = scope;
	}
	
	public abstract double getMultiModalCost(Node source, Node dest, String mode, double currentDistance, double volume);
	public abstract double getEdgeCost(Node source, Edge e, Node dest, double volume);
	
	public static double getTimeLength(Node source, Edge e, Node dest) {
		double res = 0;
		// first, we add the time to cross the edge
		if(e.hasAttribute("length") && e.hasAttribute("speed")){
			res += e.getNumber("length") / e.getNumber("speed");
		}
		else {
			// Default edge cost if length and speed have not been declared
			res += Math.hypot(source.getNumber("x")-dest.getNumber("x"), source.getNumber("y")-dest.getNumber("y"))/1000 / 50;
		}
		return res;
	}
	
	public double getTimeMultiModalCost(Node source, Node dest, String mode, double currentDistance, double volume) {
		double res = 0;
		if(((IAgent)dest.getAttribute("gama_agent")).getAttribute("handling_time_from_"+mode) != null) {
			// We first get the handling time
			double handlingTime = (double)((IAgent)dest.getAttribute("gama_agent")).getAttribute("handling_time_from_"+mode);
			res += handlingTime;

			// Then, we need to know when the next available vehicle leaves the previous multimodal node
			// To do so, we need to get the transporter on the next edge
			final IAgent transporter;
			// Here, I get the transporter agent.
			transporter = tos.getTransporter(scope, mode);
			handlingTime = (double)((IAgent)source.getAttribute("gama_agent")).getAttribute("handling_time_to_"+mode);
			GamaDate currentDate = scope.getClock().getCurrentDate().plusMillis(currentDistance*3600*1000);
			GamaDate minimalDepartureDate = currentDate.plusMillis(handlingTime*3600*1000);
			// But this object is not an instance of TransporterSkill, therefore, I need to use a static method with the agent in parameter
			GamaDate departure = TransporterSkill.getDepartureDate(scope, transporter, source, dest, minimalDepartureDate, volume);
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
		return res;
	}
}

