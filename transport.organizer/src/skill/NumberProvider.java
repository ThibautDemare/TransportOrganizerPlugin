package skill;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

public interface NumberProvider {
	public double getEdgeCost(Node source, Edge e, Node dest, double currentDistance, double volume, Node previousMultiModalNode);
}

