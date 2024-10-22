package boofcv.alg.fiducial.calib.squares;

import georegression.geometry.UtilLine2D_F64;
import georegression.metric.Distance2D_F64;
import georegression.struct.line.LineGeneral2D_F64;
import georegression.struct.line.LineSegment2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.shapes.Polygon2D_F64;
import org.ddogleg.nn.FactoryNearestNeighbor;
import org.ddogleg.nn.NearestNeighbor;
import org.ddogleg.nn.NnData;
import org.ddogleg.struct.DogArray;

import java.util.ArrayList;
import java.util.List;

public class SquaresIntoRegularClusters extends SquaresIntoClusters {

	public int maxNeighbors;

	double distanceTol = 0.2;

	double maxNeighborDistanceRatio;

	private double spaceToSquareRatio;

	protected SquareGraph graph = new SquareGraph();

	private LineGeneral2D_F64 line = new LineGeneral2D_F64();

	protected LineSegment2D_F64 lineA = new LineSegment2D_F64();
	protected LineSegment2D_F64 lineB = new LineSegment2D_F64();
	protected LineSegment2D_F64 connectLine = new LineSegment2D_F64();
	private Point2D_F64 intersection = new Point2D_F64();

	private NearestNeighbor<SquareNode> nn = FactoryNearestNeighbor.kdtree(new SquareNode.KdTreeSquareNode());
	private NearestNeighbor.Search<SquareNode> search = nn.createSearch();
	private DogArray<NnData<SquareNode>> searchResults = new DogArray(NnData::new);

	public SquaresIntoRegularClusters( double spaceToSquareRatio, int maxNeighbors, double maxNeighborDistanceRatio ) {
		this.spaceToSquareRatio = spaceToSquareRatio;
		this.maxNeighbors = maxNeighbors;
		if (this.maxNeighbors == Integer.MAX_VALUE) {
			this.maxNeighbors = Integer.MAX_VALUE - 1;
		}
		this.maxNeighborDistanceRatio = maxNeighborDistanceRatio;
	}

	public List<List<SquareNode>> process( List<Polygon2D_F64> squares ) {
		recycleData();

		computeNodeInfo(squares);

		connectNodes();

		disconnectSingleConnections();

		findClusters();
		return clusters.toList();
	}

	void computeNodeInfo( List<Polygon2D_F64> squares ) {

		for (int i = 0; i < squares.size(); i++) {
			SquareNode n = nodes.grow();
			n.reset();
			n.square = squares.get(i);

			if (n.square.size() != 4)
				throw new RuntimeException("Squares have four corners not " + n.square.size());

			graph.computeNodeInfo(n);
		}
	}

	void connectNodes() {
		setupSearch();

		for (int i = 0; i < nodes.size(); i++) {
			SquareNode n = nodes.get(i);


			double neighborDistance = n.largestSide*(1.0 + spaceToSquareRatio)*maxNeighborDistanceRatio;

			searchResults.reset();
			search.findNearest(n, neighborDistance*neighborDistance, maxNeighbors + 1, searchResults);

			for (int j = 0; j < searchResults.size(); j++) {
				NnData<SquareNode> neighbor = searchResults.get(j);
				if (neighbor.point != n)
					considerConnect(n, neighbor.point);
			}
		}
	}

	void disconnectSingleConnections() {

		List<SquareNode> open = new ArrayList<>();
		List<SquareNode> open2 = new ArrayList<>();

		for (int i = 0; i < nodes.size(); i++) {
			SquareNode n = nodes.get(i);
			checkDisconnectSingleEdge(open, n);
		}


		while (!open.isEmpty()) {
			for (int i = 0; i < open.size(); i++) {
				SquareNode n = open.get(i);
				checkDisconnectSingleEdge(open2, n);

				open.clear();
				List<SquareNode> tmp = open;
				open = open2;
				open2 = tmp;
			}
		}
	}

	private void checkDisconnectSingleEdge( List<SquareNode> open, SquareNode n ) {
		if (n.getNumberOfConnections() == 1) {
			for (int j = 0; j < n.square.size(); j++) {
				if (n.edges[j] != null) {
					open.add(n.edges[j].destination(n));
					graph.detachEdge(n.edges[j]);
					break;
				}
			}
		}
	}

	private void setupSearch() {
		nn.setPoints(nodes.toList(), false);
	}

	boolean areMiddlePointsClose( Point2D_F64 p0, Point2D_F64 p1, Point2D_F64 p2, Point2D_F64 p3 ) {
		UtilLine2D_F64.convert(p0, p3, line);

		double tol1 = p0.distance(p1)*distanceTol;

		if (Distance2D_F64.distance(line, p1) > tol1)
			return false;

		double tol2 = p2.distance(p3)*distanceTol;

		if (Distance2D_F64.distance(lineB, p2) > tol2)
			return false;

		UtilLine2D_F64.convert(p0, p1, line);
		if (Distance2D_F64.distance(line, p2) > tol2)
			return false;

		UtilLine2D_F64.convert(p3, p2, line);
		if (Distance2D_F64.distance(line, p1) > tol1)
			return false;

		return true;
	}

	void considerConnect( SquareNode node0, SquareNode node1 ) {

		lineA.a = node0.center;
		lineA.b = node1.center;

		int intersection0 = graph.findSideIntersect(node0, lineA, intersection, lineB);
		connectLine.a.setTo(intersection);
		int intersection1 = graph.findSideIntersect(node1, lineA, intersection, lineB);
		connectLine.b.setTo(intersection);

		if (intersection1 < 0 || intersection0 < 0) {
			return;
		}

		double side0 = node0.sideLengths[intersection0];
		double side1 = node1.sideLengths[intersection1];

		double sideLoc0 = connectLine.a.distance(node0.square.get(intersection0))/side0;
		double sideLoc1 = connectLine.b.distance(node1.square.get(intersection1))/side1;

		if (Math.abs(sideLoc0 - 0.5) > 0.35 || Math.abs(sideLoc1 - 0.5) > 0.35)
			return;

		double spaceDistance = connectLine.getLength();
if (Math.abs(side0 - side1)/Math.max(side0, side1) > 0.25) {
			return;
		}
if (!graph.almostParallel(node0, intersection0, node1, intersection1)) {
			return;
		}

		double ratio = Math.max(node0.smallestSide/node1.largestSide,
				node1.smallestSide/node0.largestSide);

if (ratio > 1.3)
			return;

		graph.checkConnect(node0, intersection0, node1, intersection1, spaceDistance);
	}
}
