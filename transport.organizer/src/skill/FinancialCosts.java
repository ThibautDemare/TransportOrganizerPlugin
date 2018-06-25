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
	public double getEdgeCost(Node source, Edge e, Node dest, double volume) {
		if(e.hasAttribute("blocked_edge") && (boolean)e.getAttribute("blocked_edge"))
			return Double.POSITIVE_INFINITY;
		final IAgent transporter = tos.getTransporter(scope, e.getAttribute("graph_type"));
		return e.getNumber("length") * volume * TransporterSkill.getVolumeKilometerCosts(scope, transporter);
	}

	@Override
	public double getMultiModalCost(Node source, Node dest, String mode, double currentDistance, double volume) {
		return 0;
	}}
