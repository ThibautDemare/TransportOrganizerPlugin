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

	/**
	 * It returns additional costs of multi-modal nodes
	 * @param source
	 * @param dest
	 * @param mode
	 * @param currentDistance
	 * @param volume
	 * @return
	 */
	public abstract double getMultiModalCost(Node source, Node dest, String mode, double currentDistance, double volume);

	/**
	 * It returns the costs of an edge according to the source, destination and volume
	 * @param source
	 * @param e
	 * @param dest
	 * @param volume
	 * @return
	 */
	public abstract double getEdgeCost(Node source, Edge e, Node dest, double volume);

	/**
	 * It returns the time necessary to cross an edge
	 * @param source
	 * @param e
	 * @param dest
	 * @return
	 */
	public double getFinancialCostLength(Node source, Edge e, Node dest, double volume) {
		if(e.hasAttribute("blocked_edge") && (boolean)e.getAttribute("blocked_edge"))
			return Double.POSITIVE_INFINITY;
		final IAgent transporter = tos.getTransporter(scope, e.getAttribute("graph_type"));
		return e.getNumber("length") * volume * TransporterSkill.getVolumeKilometerCosts(scope, transporter);
	}

	/**
	 * It returns the time necessary to cross an edge
	 * @param source
	 * @param e
	 * @param dest
	 * @return
	 */
	public static double getTimeLength(Node source, Edge e, Node dest) {
		if(e.hasAttribute("blocked_edge") && (boolean)e.getAttribute("blocked_edge"))
			return Double.POSITIVE_INFINITY;

		if(e.hasAttribute("length") && e.hasAttribute("speed")){
			return e.getNumber("length") / e.getNumber("speed");
		}
		else {
			// Default edge cost if length and speed have not been declared
			return (Math.hypot(source.getNumber("x")-dest.getNumber("x"), source.getNumber("y")-dest.getNumber("y"))/1000.0) / 50.0;
		}
	}

	/**
	 * It returns the time necessary to cross a multi-modal node
	 * @param source
	 * @param dest
	 * @param mode
	 * @param currentDistance
	 * @param volume
	 * @return
	 */
	public double getTimeMultiModalCost(Node source, Node dest, String mode, double currentDistance, double volume) {
		double res = 0;
		if(((IAgent)dest.getAttribute("gama_agent")).getAttribute("handling_time_from_"+mode) != null) {
			// We need to know when the next available vehicle leaves the previous multimodal node (the source node)

			// First, we get the date when the goods arrive to the source node
			GamaDate currentDate = scope.getClock().getCurrentDate().plusMillis(currentDistance*3600*1000);
			// We get the handling time to make the goods leave the source node and add this duration to the "currentDate"
			double handlingTime = (double)((IAgent)source.getAttribute("gama_agent")).getAttribute("handling_time_to_"+mode);
			GamaDate minimalDepartureDate = currentDate.plusMillis(handlingTime*3600*1000);

			// Then, we can determine the departure date of the vehicle. To do so, we need to get the transporter agent on the next edge
			final IAgent transporter = tos.getTransporter(scope, mode);
			// But "transporter" is not an instance of TransporterSkill, therefore, I need to use a static method with the agent in parameter
			GamaDate departure = TransporterSkill.getDepartureDate(scope, transporter, source, dest, minimalDepartureDate, volume);

			// Now, I know the departure date. I compute the time between the currentDate and the departure, and convert it in hours
			double hours = currentDate.until(departure, ChronoUnit.HOURS);
			res += hours;

			// Eventually, we add the time to enter the destination node
			res += (double)((IAgent)dest.getAttribute("gama_agent")).getAttribute("handling_time_from_"+mode);
		}
		return res;
	}
}

