package skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Stack;

import org.graphstream.algorithm.AbstractSpanningTree;
import org.graphstream.algorithm.util.FibonacciHeap;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.Path;


public class SubDijkstra extends AbstractSpanningTree {
	protected static class Data {
		FibonacciHeap<Double, Node>.Node fn;
		Edge edgeFromParent;
		double distance;
	}

	protected String resultAttribute;
	protected NumberProvider numberProvider;
	protected Node source;

	// *** Helpers ***

	protected double getLength(Node source, Edge edge, Node dest, double currentDistance, double volume, Node multiModalSource) {
		double length = 0;
		length += numberProvider == null ? 1 : numberProvider.getEdgeCost(source, edge, dest, currentDistance, volume, multiModalSource);
		if (length < 0)
			throw new IllegalStateException("Edge " + edge.getId()
					+ " has negative length " + length);
		return length;
	}

	protected double getSourceLength() {
		return 0;
	}

	// *** Constructors ***

	/**
	 * Constructs an instance with the specified parameters. The edges of the shortest path tree are not tagged.
	 * 
	 * @param resultAttribute
	 *            Attribute name used to store internal solution data in the
	 *            nodes of the graph. If {@code null}, a unique name is chosen
	 *            automatically.
	 */
	public SubDijkstra(String resultAttribute,
			NumberProvider numberProvider) {
		this(resultAttribute, numberProvider, null, null, null);
	}
	
	/**
	 * Constructs an instance with the specified parameters.
	 * 
	 * @param element
	 *            Graph elements (edges or/and nodes) used to compute the path
	 *            lengths. If {@code null}, the length of the path is computed
	 *            using edges.
	 * @param resultAttribute
	 *            Attribute name used to store internal solution data in the
	 *            nodes of the graph. If {@code null}, a unique name is chosen
	 *            automatically.
	 * @param lengthAttribute
	 *            Attribute name used to define individual element lengths. If
	 *            {@code null} the length of the elements is considered to be
	 *            one.
	 * @param flagAttribute
	 *            attribute used to set if an edge is in the spanning tree
	 * @param flagOn
	 *            value of the <i>flagAttribute</i> if edge is in the spanning
	 *            tree
	 * @param flagOff
	 *            value of the <i>flagAttribute</i> if edge is not in the
	 *            spanning tree
	 */
	public SubDijkstra(String resultAttribute, NumberProvider numberProvider, String flagAttribute, Object flagOn, Object flagOff) {
		super("flagAttribute", "flagOn", "flagOff");
		this.resultAttribute = resultAttribute == null ? toString()
				+ "_result_" : resultAttribute;
		this.numberProvider = numberProvider;
		graph = null;
		source = null;
	}

	// *** Some basic methods ***

	/**
	 * Dijkstra's algorithm computes shortest paths from a given source node to
	 * all nodes in a graph. This method returns the source node.
	 * 
	 * @return the source node
	 * @see #setSource(Node)
	 */
	@SuppressWarnings("unchecked")
	public <T extends Node> T getSource() {
		return (T) source;
	}

	/**
	 * Dijkstra's algorithm computes shortest paths from a given source node to
	 * all nodes in a graph. This method sets the source node.
	 * 
	 * @param source
	 *            The new source node.
	 * @see #getSource()
	 */
	public void setSource(Node source) {
		this.source = source;
	}

	/**
	 * Removes the attributes used to store internal solution data in the nodes
	 * of the graph. Use this method to free memory. Solution access methods
	 * must not be used after calling this method.
	 */
	@Override
	public void clear() {
		super.clear();
		for (Node node : graph) {
			Data data = node.getAttribute(resultAttribute);
			if (data != null) {
				data.fn = null;
				data.edgeFromParent = null;
			}
			node.removeAttribute(resultAttribute);
		}
	}

	// *** Methods of Algorithm interface ***


	/**
	 * Computes the shortest paths from the source node to all nodes in the
	 * graph.
	 * 
	 * @throws IllegalStateException
	 *             if {@link #init(Graph)} or {@link #setSource(Node)} have not
	 *             been called before or if elements with negative lengths are
	 *             discovered.
	 * @see org.graphstream.algorithm.Algorithm#compute()
	 * @complexity O(<em>m</em> + <em>n</em>log<em>n</em>) where <em>m</em> is
	 *             the number of edges and <em>n</em> is the number of nodes in
	 *             the graph.
	 */
	public void compute(double volume) {
		// check if computation can start
		if (graph == null)
			throw new IllegalStateException(
					"No graph specified. Call init() first.");
		if (source == null)
			throw new IllegalStateException(
					"No source specified. Call setSource() first.");
		resetFlags();
		makeTree(volume);
	}
	
	@Override
	protected void makeTree() {
		makeTree(1.);
	}

	protected void makeTree(double volume) {
		// initialization
		FibonacciHeap<Double, Node> heap = new FibonacciHeap<Double, Node>();
		for (Node node : graph) {
			Data data = new Data();
			double v = node == source ? getSourceLength()
					: Double.POSITIVE_INFINITY;
			data.fn = heap.add(v, node);
			data.edgeFromParent = null;
			node.addAttribute(resultAttribute, data);
		}
		// main loop
		while (!heap.isEmpty()) {
			Node u = heap.extractMin();
			Data dataU = u.getAttribute(resultAttribute);
			dataU.distance = dataU.fn.getKey();
			dataU.fn = null;
			if (dataU.edgeFromParent != null)
				edgeOn(dataU.edgeFromParent);
			for (Edge e : u.getEachLeavingEdge()) {
				Node v = e.getOpposite(u);
				Data dataV = v.getAttribute(resultAttribute);
				if (dataV.fn == null)
					continue;
				double tryDist;
				if(!u.hasAttribute("multiModalNode") && u.getAttribute("multimodalSource") == null)
					tryDist = dataU.distance;
				else
					tryDist = dataU.distance + getLength(u, e, v, dataU.distance, volume, u.hasAttribute("multiModalNode")?u:u.getAttribute("multimodalSource"));
				if (tryDist < dataV.fn.getKey()) {
					if(u.hasAttribute("multiModalNode")){
						v.addAttribute("multimodalSource", u);
					}
					else {
						v.addAttribute("multimodalSource", (Node) u.getAttribute("multimodalSource"));
					};
					dataV.edgeFromParent = e;
					heap.decreaseKey(dataV.fn, tryDist);
				}
			}
		}
	}

	// *** Iterators ***

	protected class NodeIterator<T extends Node> implements Iterator<T> {
		protected Node nextNode;

		protected NodeIterator(Node target) {
			nextNode = Double.isInfinite(getPathLength(target)) ? null : target;
		}

		public boolean hasNext() {
			return nextNode != null;
		}

		@SuppressWarnings("unchecked")
		public T next() {
			if (nextNode == null)
				throw new NoSuchElementException();
			Node node = nextNode;
			nextNode = getParent(nextNode);
			return (T) node;
		}

		public void remove() {
			throw new UnsupportedOperationException(
					"remove is not supported by this iterator");
		}
	}

	protected class EdgeIterator<T extends Edge> implements Iterator<T> {
		protected Node nextNode;
		protected T nextEdge;

		protected EdgeIterator(Node target) {
			nextNode = target;
			nextEdge = getEdgeFromParent(nextNode);
		}

		public boolean hasNext() {
			return nextEdge != null;
		}

		public T next() {
			if (nextEdge == null)
				throw new NoSuchElementException();
			T edge = nextEdge;
			nextNode = getParent(nextNode);
			nextEdge = getEdgeFromParent(nextNode);
			return edge;
		}

		public void remove() {
			throw new UnsupportedOperationException(
					"remove is not supported by this iterator");
		}
	}

	protected class PathIterator implements Iterator<Path> {
		protected List<Node> nodes;
		protected List<Iterator<Edge>> iterators;
		protected Path nextPath;
		protected double volume;

		protected void extendPathStep() {
			int last = nodes.size() - 1;
			Node v = nodes.get(last);
			double lengthV = getPathLength(v);
			Iterator<Edge> it = iterators.get(last);
			while (it.hasNext()) {
				Edge e = it.next();
				Node u = e.getOpposite(v);
				if (getPathLength(u) + getLength(u, e, v, getPathLength(u), volume, v.getAttribute("multiModalSource")) == lengthV) {
					nodes.add(u);
					iterators.add(u.getEnteringEdgeIterator());
					return;
				}
			}
			nodes.remove(last);
			iterators.remove(last);
		}

		protected void extendPath() {
			while (!nodes.isEmpty() && nodes.get(nodes.size() - 1) != source)
				extendPathStep();
		}

		protected void constructNextPath() {
			if (nodes.isEmpty()) {
				nextPath = null;
				return;
			}
			nextPath = new Path();
			nextPath.setRoot(source);
			for (int i = nodes.size() - 1; i > 0; i--)
				nextPath.add(nodes.get(i).getEdgeToward(
						nodes.get(i - 1).getId()));
		}

		public PathIterator(Node target, double volume) {
			nodes = new ArrayList<Node>();
			iterators = new ArrayList<Iterator<Edge>>();
			this.volume = volume;
			if (Double.isInfinite(getPathLength(target))) {
				nextPath = null;
				return;
			}
			nodes.add(target);
			iterators.add(target.getEnteringEdgeIterator());
			extendPath();
			constructNextPath();
		}

		public boolean hasNext() {
			return nextPath != null;
		}

		public Path next() {
			if (nextPath == null)
				throw new NoSuchElementException();
			nodes.remove(nodes.size() - 1);
			iterators.remove(iterators.size() - 1);
			extendPath();
			Path path = nextPath;
			constructNextPath();
			return path;
		}

		public void remove() {
			throw new UnsupportedOperationException(
					"remove is not supported by this iterator");
		}
	}

	protected class TreeIterator<T extends Edge> implements Iterator<T> {
		Iterator<Node> nodeIt;
		T nextEdge;

		protected void findNextEdge() {
			nextEdge = null;
			while (nodeIt.hasNext() && nextEdge == null)
				nextEdge = getEdgeFromParent(nodeIt.next());
		}

		protected TreeIterator() {
			nodeIt = graph.getNodeIterator();
			findNextEdge();
		}

		public boolean hasNext() {
			return nextEdge != null;
		}

		public T next() {
			if (nextEdge == null)
				throw new NoSuchElementException();
			T edge = nextEdge;
			findNextEdge();
			return edge;
		}

		public void remove() {
			throw new UnsupportedOperationException(
					"remove is not supported by this iterator");
		}
	}

	// *** Methods to access the solution ***

	/**
	 * Returns the length of the shortest path from the source node to a given
	 * target node.
	 * 
	 * @param target
	 *            A node
	 * @return the length of the shortest path or
	 *         {@link java.lang.Double#POSITIVE_INFINITY} if there is no path
	 *         from the source to the target
	 * @complexity O(1)
	 */
	public double getPathLength(Node target) {
		return target.<Data> getAttribute(resultAttribute).distance;
	}

	/**
	 * Dijkstra's algorithm produces a shortest path tree rooted in the source
	 * node. This method returns the total length of the tree.
	 * 
	 * @return the length of the shortest path tree
	 * @complexity O(<em>n</em>) where <em>n</em> is the number of nodes is the
	 *             graph.
	 */
	public double getTreeLength(double volume) {
		double length = getSourceLength();
		Node previousMultiModalNode = source;
		for (Edge edge : getTreeEdges()) {
			Node node = edge.getNode0();
			if (getEdgeFromParent(node) != edge)
				node = edge.getNode1();
			length += getLength(edge.getOpposite(node), edge, node, length, volume, previousMultiModalNode);
			if(node.hasAttribute("multiModalNode"))
				previousMultiModalNode = node;
		}
		return length;
	}

	/**
	 * Returns the edge between the target node and the previous node in the
	 * shortest path from the source to the target. This is also the edge
	 * connecting the target to its parent in the shortest path tree.
	 * 
	 * @param target
	 *            a node
	 * @return the edge between the target and its predecessor in the shortest
	 *         path, {@code null} if there is no path from the source to the
	 *         target or if the target and the source are the same node.
	 * @see #getParent(Node)
	 * @complexity O(1)
	 */
	@SuppressWarnings("unchecked")
	public <T extends Edge> T getEdgeFromParent(Node target) {
		return (T) target.<Data> getAttribute(resultAttribute).edgeFromParent;
	}

	/**
	 * Returns the node preceding the target in the shortest path from the
	 * source to the target. This node is the parent of the target in the
	 * shortest path tree.
	 * 
	 * @param target
	 *            a node
	 * @return the predecessor of the target in the shortest path, {@code null}
	 *         if there is no path from the source to the target or if the
	 *         target and the source are the same node.
	 * @see #getEdgeFromParent(Node)
	 * @complexity O(1)
	 */
	public <T extends Node> T getParent(Node target) {
		Edge edge = getEdgeFromParent(target);
		if (edge == null)
			return null;
		return edge.getOpposite(target);
	}

	/**
	 * This iterator traverses the nodes on the shortest path from the source
	 * node to a given target node. The nodes are traversed in reverse order:
	 * the target node first, then its predecessor, ... and finally the source
	 * node. If there is no path from the source to the target, no nodes are
	 * traversed. This iterator does not support
	 * {@link java.util.Iterator#remove()}.
	 * 
	 * @param target
	 *            a node
	 * @return an iterator on the nodes of the shortest path from the source to
	 *         the target
	 * @see #getPathNodes(Node)
	 * @complexity Each call of {@link java.util.Iterator#next()} of this
	 *             iterator takes O(1) time
	 */
	public <T extends Node> Iterator<T> getPathNodesIterator(Node target) {
		return new NodeIterator<T>(target);
	}

	/**
	 * An iterable view of the nodes on the shortest path from the source node
	 * to a given target node. Uses {@link #getPathNodesIterator(Node)}.
	 * 
	 * @param target
	 *            a node
	 * @return an iterable view of the nodes on the shortest path from the
	 *         source to the target
	 * @see #getPathNodesIterator(Node)
	 */
	public <T extends Node> Iterable<T> getPathNodes(final Node target) {
		return new Iterable<T>() {
			public Iterator<T> iterator() {
				return getPathNodesIterator(target);
			}
		};
	}

	/**
	 * This iterator traverses the edges on the shortest path from the source
	 * node to a given target node. The edges are traversed in reverse order:
	 * first the edge between the target and its predecessor, ... and finally
	 * the edge between the source end its successor. If there is no path from
	 * the source to the target or if he source and the target are the same
	 * node, no edges are traversed. This iterator does not support
	 * {@link java.util.Iterator#remove()}.
	 * 
	 * @param target
	 *            a node
	 * @return an iterator on the edges of the shortest path from the source to
	 *         the target
	 * @see #getPathEdges(Node)
	 * @complexity Each call of {@link java.util.Iterator#next()} of this
	 *             iterator takes O(1) time
	 */
	public <T extends Edge> Iterator<T> getPathEdgesIterator(Node target) {
		return new EdgeIterator<T>(target);
	}

	/**
	 * An iterable view of the edges on the shortest path from the source node
	 * to a given target node. Uses {@link #getPathEdgesIterator(Node)}.
	 * 
	 * @param target
	 *            a node
	 * @return an iterable view of the edges on the shortest path from the
	 *         source to the target
	 * @see #getPathEdgesIterator(Node)
	 */
	public <T extends Edge> Iterable<T> getPathEdges(final Node target) {
		return new Iterable<T>() {
			public Iterator<T> iterator() {
				return getPathEdgesIterator(target);
			}

		};
	}

	/**
	 * This iterator traverses <em>all</em> the shortest paths from the source
	 * node to a given target node. If there is more than one shortest paths
	 * between the source and the target, other solution access methods choose
	 * one of them (the one from the shortest path tree). This iterator can be
	 * used if one needs to know all the paths. Each call to
	 * {@link java.util.Iterator#next()} method of this iterator returns a
	 * shortest path in the form of {@link org.graphstream.graph.Path} object.
	 * This iterator does not support {@link java.util.Iterator#remove()}.
	 * 
	 * @param target
	 *            a node
	 * @return an iterator on all the shortest paths from the source to the
	 *         target
	 * @see #getAllPaths(Node)
	 * @complexity Each call of {@link java.util.Iterator#next()} of this
	 *             iterator takes O(<em>m</em>) time in the worst case, where
	 *             <em>m</em> is the number of edges in the graph
	 */
	public Iterator<Path> getAllPathsIterator(Node target, double volume) {
		return new PathIterator(target, volume);
	}

	/**
	 * An iterable view of of <em>all</em> the shortest paths from the source
	 * node to a given target node. Uses {@link #getAllPathsIterator(Node)}
	 * 
	 * @param target
	 *            a node
	 * @return an iterable view of all the shortest paths from the source to the
	 *         target
	 * @see #getAllPathsIterator(Node)
	 */
	public Iterable<Path> getAllPaths(final Node target, double volume) {
		return new Iterable<Path>() {
			public Iterator<Path> iterator() {
				return getAllPathsIterator(target, volume);
			}
		};
	}

	/**
	 * Dijkstra's algorithm produces a shortest path tree rooted in the source
	 * node. This iterator traverses the edges of this tree. The edges are
	 * traversed in no particular order.
	 * 
	 * @return an iterator on the edges of the shortest path tree
	 * @see #getTreeEdges()
	 * @complexity Each call of {@link java.util.Iterator#next()} of this
	 *             iterator takes O(1) time
	 */
	@Override
	public <T extends Edge> Iterator<T> getTreeEdgesIterator() {
		return new TreeIterator<T>();
	}


	/**
	 * Returns the shortest path from the source node to a given target node. If
	 * there is no path from the source to the target returns an empty path.
	 * This method constructs a {@link org.graphstream.graph.Path} object which
	 * consumes heap memory proportional to the number of edges and nodes in the
	 * path. When possible, prefer using {@link #getPathNodes(Node)} and
	 * {@link #getPathEdges(Node)} which are more memory- and time-efficient.
	 * 
	 * @param target
	 *            a node
	 * @return the shortest path from the source to the target
	 * @complexity O(<em>p</em>) where <em>p</em> is the number of the nodes in
	 *             the path
	 */
	public Path getPath(Node target) {
		Path path = new Path();
		if (Double.isInfinite(getPathLength(target)))
			return path;
		Stack<Edge> stack = new Stack<Edge>();
		for (Edge e : getPathEdges(target))
			stack.push(e);
		path.setRoot(source);
		while (!stack.isEmpty())
			path.add(stack.pop());
		return path;
	}
}