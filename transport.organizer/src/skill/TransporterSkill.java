package skill;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

import msi.gama.metamodel.agent.IAgent;
import msi.gama.precompiler.GamlAnnotations.doc;
import msi.gama.precompiler.GamlAnnotations.skill;
import msi.gama.runtime.IScope;
import msi.gama.util.GamaDate;
import msi.gama.util.IList;
import msi.gaml.skills.Skill;

@doc("The transporter agents must have this skill.")
@skill(name = IKeywordTOAdditional.TRANSPORTER)
public class TransporterSkill extends Skill{

	public static double getVolumeKilometerCosts(final IScope scope, IAgent transporter){
		return (double) transporter.getAttribute("volumeKilometersCosts");
	}

	public static GamaDate getDepartureDate(final IScope scope, IAgent transporter, 
			Node source, Node destination, GamaDate minimalDepartureDate, double volume) {
		// We get the gama agent associated to the GS node
		IAgent buildingSource = source.getAttribute("gama_agent");
		// Then we get the list of leaving vehicle of this node
		IList<IAgent> vehicles = (IList<IAgent>) buildingSource.getAttribute("leavingVehicles_"+(String)transporter.getAttribute("networkType"));
		vehicles.sort(new Comparator<IAgent>() {
			public int compare(IAgent o1, IAgent o2) {
				GamaDate departureDateO1 = (GamaDate) o1.getAttribute("departureDate");
				GamaDate departureDateO2 = (GamaDate) o2.getAttribute("departureDate");
				if(departureDateO1.isSmallerThan(departureDateO2, true)) {
					return -1;
				}
				if(departureDateO1.isGreaterThan(departureDateO2, true)) {
					return 1;
				}
				return 0;
			}
		});

		// Then, we browse these vehicles,
		// and we try to find a vehicle which can carry the volume of goods and whose the departure date is OK (meaning: we have the time to handle the goods before the vehicles actually leaves)
		// and whose the destination is the same
		IAgent vehicle = null;
		for(int i = 0; i < vehicles.size(); i++){
			IAgent currentVehicle = (IAgent) vehicles.get(i);
			if(destination.getAttribute("gama_agent") == currentVehicle.getAttribute("destination")){
				if(((double) currentVehicle.getAttribute("scheduledTransportedVolume") + volume) <= (double) transporter.getAttribute("maximalTransportedVolume")){
					GamaDate departureDate = (GamaDate) currentVehicle.getAttribute("departureDate");
					if(departureDate.isGreaterThan(minimalDepartureDate, false)){
						if(vehicle == null)
							vehicle = currentVehicle;
						else if( ((GamaDate)vehicle.getAttribute("departureDate")).isGreaterThan(departureDate, false))
							vehicle = currentVehicle;
					}
				}
			}
		}
		GamaDate returnedDate = null;
		// If we did not find a vehicle, we do as if we create a new one
		// If we did not find a vehicle, we create a new one
		if(vehicle == null){
			returnedDate = findBestDepartureDate(scope, transporter, buildingSource, vehicles, minimalDepartureDate);
		}
		else {
			// There is an available vehicle
			// But can't we create a new vehicle which leaves even sooner the building?
			GamaDate bestOtherDate = findBestDepartureDate(scope, transporter, buildingSource, vehicles, minimalDepartureDate);
			if( ((GamaDate)vehicle.getAttribute("departureDate")).isGreaterThan(bestOtherDate, true)) {
				returnedDate =  bestOtherDate;
			}
			else {
				// We did not find such a vehicle. Therefore, the vehicle we found is the best one
				// So we use this vehicle and we need to add the commodity to the transported commodities
				returnedDate = (GamaDate)vehicle.getAttribute("departureDate");
			}
		}
		return returnedDate;
	}

	public static void registerDepartureDate(final IScope scope, IAgent transporter, IAgent commodity, Node currentNode, Node destination, GamaDate minimalDepartureDate) {
		// We get the gama agent associated to the GS node
		IAgent building = currentNode.getAttribute("gama_agent");
		// Then we get the list of leaving vehicle of this node
		IList<IAgent> vehicles = (IList<IAgent>) building.getAttribute("leavingVehicles_"+(String)transporter.getAttribute("networkType"));
		vehicles.sort(new Comparator<IAgent>() {
			public int compare(IAgent o1, IAgent o2) {
				GamaDate departureDateO1 = (GamaDate) o1.getAttribute("departureDate");
				GamaDate departureDateO2 = (GamaDate) o2.getAttribute("departureDate");
				if(departureDateO1.isSmallerThan(departureDateO2, true)) {
					return -1;
				}
				if(departureDateO1.isGreaterThan(departureDateO2, true)) {
					return 1;
				}
				return 0;
			}
		});
		// Then, we browse these vehicles,
		// and we try to find a vehicle which can carry the volume of goods and whose the departure date is OK (meaning: we have the time to handle the goods before the vehicles actually leaves)
		IAgent vehicle = null;
		boolean notfound = true;
		for(int i = 0; i < vehicles.size() && notfound; i++){
			IAgent currentVehicle = (IAgent) vehicles.get(i);
			if(destination.getAttribute("gama_agent") == currentVehicle.getAttribute("destination")){
				if(((double) currentVehicle.getAttribute("scheduledTransportedVolume") + (double)commodity.getAttribute("volume")) <= (double) transporter.getAttribute("maximalTransportedVolume")){
					GamaDate departureDate = (GamaDate) currentVehicle.getAttribute("departureDate");
					if(departureDate.isGreaterThan(minimalDepartureDate, false)){
						vehicle = currentVehicle;
						notfound = false;
					}
				}
			}
		}
		// If we did not find a vehicle, we create a new one
		if(vehicle == null){
			vehicle = createVehicle(scope, transporter, commodity, currentNode, destination);
			vehicle.setAttribute("departureDate", findBestDepartureDate(scope, transporter, building, vehicles, minimalDepartureDate));
			vehicles.add(vehicle);
		}
		else {
			// There is an available vehicle
			// But can't we create a new vehicle which leaves even sooner the building?
			GamaDate bestOtherDate = findBestDepartureDate(scope, transporter, building, vehicles, minimalDepartureDate);
			if( ((GamaDate)vehicle.getAttribute("departureDate")).isGreaterThan(bestOtherDate, true)) {
				vehicle = createVehicle(scope, transporter, commodity, currentNode, destination);
				vehicle.setAttribute("departureDate", bestOtherDate);
				vehicles.add(vehicle);
			}
			else {
				// We did not find such a vehicle. Therefore, the vehicle we found is the best one
				// So we use this vehicle and we need to add the commodity to the transported commodities
				vehicle.setAttribute("scheduledTransportedVolume",
						((double) vehicle.getAttribute("scheduledTransportedVolume")) + ((double) commodity.getAttribute("volume")));
				IList scheduledCommodities = ((IList)vehicle.getAttribute("scheduledCommodities"));
				scheduledCommodities.add(commodity);
				vehicle.setAttribute("scheduledCommodities", scheduledCommodities);
			}
		}
	}

	/**
	 * // This method find the best new departure date and return it
	 * @param scope
	 * @param transporter
	 * @param source
	 * @return
	 */
	private static GamaDate findBestDepartureDate(final IScope scope, IAgent transporter, IAgent source, IList<IAgent> vehicles, GamaDate minimalDepartureDate) {
		GamaDate lastDepartureDate = ((GamaDate)source.getAttribute("lastVehicleDeparture_"+(String)transporter.getAttribute("networkType")));

		// Therefore, no vehicle ever leaves the building. It is the first time
		if(lastDepartureDate == null && vehicles.size() == 0) {
			return minimalDepartureDate;// So, we can go ASAP
		}

		double timeBetweenVehicle = (double)transporter.getAttribute("timeBetweenVehicles")*3600*1000;

		// Il y a déjà eu un départ, mais il n'y en a pas d'autres de prévu
		if(lastDepartureDate != null && vehicles.size() == 0) {
			if(lastDepartureDate.plusMillis(timeBetweenVehicle).isSmallerThan(minimalDepartureDate, false)) {
				return minimalDepartureDate;
			}
			else {
				return lastDepartureDate.plusMillis(timeBetweenVehicle);
			}
		}

		// Il n'y a jamais eu de départ, mais il y en a de prévu prochainement (lastDepartureDate n'est renseigné qu'au moment du départ effectif du véhicule)
		if(lastDepartureDate == null && 0 <= vehicles.size()) {
			if(minimalDepartureDate.plusMillis(timeBetweenVehicle).isSmallerThan(((GamaDate)vehicles.get(0).getAttribute("departureDate")), false)) {
				return minimalDepartureDate;
			}
		}

		// Il y a déjà eu un départ, et il y en a d'autres de prévus. Peut-on caser notre départ avant le premier véhicule déjà prévu?
		if(lastDepartureDate != null  && 0 <= vehicles.size()) {
			GamaDate dv0 = ((GamaDate)vehicles.get(0).getAttribute("departureDate"));
			// Dans tous les cas (minimalDepartureDate + timeBetweenVehicle) doit être avant dv0
			if(dv0.isGreaterThan(minimalDepartureDate.plusMillis(timeBetweenVehicle), false)) {
				// On est dans le cas où la date de départ final pourrait se trouver entre lastDepartureDate et dv0
				// Le mieux serait que la date de départ soit minimalDepartureDate
				// mais c'est à condition que ça respecte les contraintes de timeBetweenVehicle
				if(minimalDepartureDate.isGreaterThan(lastDepartureDate.plusMillis(timeBetweenVehicle), false) ) {
					return minimalDepartureDate;
				}
				// Si les conditions ne sont pas respectées, alors le dernier recours serait que la date de départ soit lastDepartureDate.plusMillis(timeBetweenVehicle). Sinon la date de départ sera forcément après dv0
				else if( lastDepartureDate.plusMillis(timeBetweenVehicle*2).isSmallerThan(dv0, false) ){
					return lastDepartureDate.plusMillis(timeBetweenVehicle);
				}
			}
		}

		// Si on a encore rien retourné, alors, on doit retourner une date nécessairement après la date de départ du premier véhicule
		// Soit il s'agit d'une date "coincée" entre deux véhicules prévus au départ
		for(int i = 0; i < vehicles.size() - 1; i ++) {
			IAgent v2 = (IAgent) vehicles.get(i + 1);
			GamaDate d2 = ((GamaDate) v2.getAttribute("departureDate"));
			// Dans tous les cas (minimalDepartureDate + timeBetweenVehicle) doit être avant d2
			if(d2.isGreaterThan(minimalDepartureDate.plusMillis(timeBetweenVehicle), false)) {
				// On est dans le cas où la date de départ final pourrait se trouver entre d1 et d2
				IAgent v1 = (IAgent) vehicles.get(i);
				GamaDate d1 = ((GamaDate) v1.getAttribute("departureDate"));
				// Le mieux serait que la date de départ soit minimalDepartureDate
				// mais c'est à condition que ça respecte les contraintes de timeBetweenVehicle
				if(minimalDepartureDate.isGreaterThan(d1.plusMillis(timeBetweenVehicle), false) ) {
					return minimalDepartureDate;
				}
				// Si les conditions ne sont pas respectées, alors le dernier recours serait que la date de départ soit d1.plusMillis(timeBetweenVehicle). Sinon la date de départ sera forcément après d2
				else if( d1.plusMillis(timeBetweenVehicle*2).isSmallerThan(d2, false) ){
					return d1.plusMillis(timeBetweenVehicle);
				}
			}
		}

		// soit il s'agit d'une date après le dernier véhicule
		GamaDate lastDate = (GamaDate) vehicles.get(vehicles.size()-1).getAttribute("departureDate");
		if( minimalDepartureDate.isGreaterThan(lastDate.plusMillis(timeBetweenVehicle), false) ) {
			return minimalDepartureDate;
		}
		else {
			return lastDate.plusMillis(timeBetweenVehicle);
		}
	}

	private static IAgent createVehicle(final IScope scope,IAgent transporter, IAgent commodity, Node currentNode, Node destination) {
		IAgent vehicle = scope.getSimulation().getPopulationFor("Vehicle").createAgents(scope, 1, null, false, false).get(0);
		vehicle.setLocation(((IAgent)currentNode.getAttribute("gama_agent")).getLocation());
		vehicle.setAttribute("scheduledTransportedVolume", ((double) commodity.getAttribute("volume")));
		IList scheduledCommodities = ((IList)vehicle.getAttribute("scheduledCommodities"));
		scheduledCommodities.add(commodity);
		vehicle.setAttribute("scheduledCommodities", scheduledCommodities);
		vehicle.setAttribute("destination", destination.getAttribute("gama_agent"));
		vehicle.setAttribute("source", currentNode.getAttribute("gama_agent"));
		vehicle.setAttribute("networkType", transporter.getAttribute("networkType"));
		return vehicle;
	}

	private static GamaDate roundDate(final IScope scope, GamaDate date) {
		GamaDate returnedDate = new GamaDate(scope, date);
		if(returnedDate.getSecond() != 0)
			returnedDate = returnedDate.plus(60-returnedDate.getSecond(), ChronoUnit.SECONDS);
		if(returnedDate.getMinute() != 0 )
			returnedDate = returnedDate.plus(60-returnedDate.getMinute(), ChronoUnit.MINUTES);
		return returnedDate;
	}
}
