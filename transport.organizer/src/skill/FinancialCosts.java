package skill;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

import msi.gama.metamodel.agent.IAgent;
import msi.gama.runtime.IScope;
import msi.gama.util.GamaDate;

public class FinancialCosts extends NumberProvider {
	public FinancialCosts(final TransportOrganizerSkill tos, final IScope scope){
		super(tos, scope);
	}

	@Override
	public Double getEdgeCost(Node source, Edge e, Node dest, double volume) {
		return getFinancialCostLength(source, e, dest, volume);
	}

	@Override
	public Double getMultiModalCost(Node source, Node dest, String mode, double currentDistance, double volume) {
		// We first check if the constraint of time between vehicles allows some departure between the source and the destination
		//To do so, we need to get the transporter agent on the next edge
		final IAgent transporter = tos.getTransporter(scope, mode);
		if(TransporterSkill.getTimeBeweenVehicleDest(scope, transporter, source.getAttribute("gama_agent"), dest.getAttribute("gama_agent")) == Double.POSITIVE_INFINITY){
			return Double.POSITIVE_INFINITY;
		}
		// If there is no constraint, then, we return a cost of one here.
		return 1.0;
	}
}