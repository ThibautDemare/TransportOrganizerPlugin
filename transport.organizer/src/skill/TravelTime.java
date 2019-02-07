package skill;

import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalUnit;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

import msi.gama.metamodel.agent.IAgent;
import msi.gama.runtime.IScope;
import msi.gama.util.GamaDate;

public class TravelTime extends NumberProvider {


	public TravelTime(TransportOrganizerSkill tos, IScope scope) {
		super(tos, scope);
	}

	public double getEdgeCost(Node source, Edge e, Node dest, double volume) {
		return NumberProvider.getTimeLength(source, e, dest);
	}

	public double getMultiModalCost(Node source, Node dest, String mode, double currentDistance, double volume) {
		return getTimeMultiModalCost(source, dest, mode, currentDistance, volume);
	}
}