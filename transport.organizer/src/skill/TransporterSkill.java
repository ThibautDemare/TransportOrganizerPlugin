package skill;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

import msi.gama.metamodel.agent.IAgent;
import msi.gama.precompiler.GamlAnnotations.doc;
import msi.gama.precompiler.GamlAnnotations.skill;
import msi.gama.runtime.IScope;
import msi.gama.util.GamaDate;
import msi.gama.util.GamaMap;
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
		IAgent dest = destination.getAttribute("gama_agent");

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
		if(vehicle == null){
			returnedDate = findBestDepartureDate(scope, transporter, buildingSource, dest, vehicles, minimalDepartureDate);
		}
		else {
			// There is an available vehicle
			// But can't we create a new vehicle which leaves even sooner the building?
			GamaDate bestOtherDate = findBestDepartureDate(scope, transporter, buildingSource, dest, vehicles, minimalDepartureDate);
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
		IAgent dest = destination.getAttribute("gama_agent");
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
			vehicle.setAttribute("departureDate", findBestDepartureDate(scope, transporter, building, dest, vehicles, minimalDepartureDate));
			vehicles.add(vehicle);
		}
		else {
			// There is an available vehicle
			// But can't we create a new vehicle which leaves even sooner the building?
			GamaDate bestOtherDate = findBestDepartureDate(scope, transporter, building, dest, vehicles, minimalDepartureDate);
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
	private static GamaDate findBestDepartureDate(final IScope scope, IAgent transporter, IAgent source, IAgent dest, IList<IAgent> vehicles, GamaDate minimalDepartureDate) {
		GamaDate lastDepartureDate = ((GamaDate)source.getAttribute("lastVehicleDeparture_"+(String)transporter.getAttribute("networkType")));

		// No vehicle ever left the building. It is the first time
		if(lastDepartureDate == null && vehicles.size() == 0) {
			return minimalDepartureDate;// So, we can go ASAP
		}

		GamaDate lastDepartureDateDest = ((GamaDate)source.getAttribute("lastVehicleDepartureDest_"+(String)transporter.getAttribute("networkType")+"_"+(String)dest.getAttribute("cityName")));

		double timeBetweenVehicle = (double)transporter.getAttribute("timeBetweenVehicles")*3600*1000;
		double timeBetweenVehicleDest = getTimeBeweenVehicleDest(scope, transporter, source, dest);

		// Il y a déjà eu un départ, mais il n'y en a pas d'autres de prévu
		if(lastDepartureDate != null && vehicles.size() == 0) {
			// on part donc dès que possible après lastDepartureDate, mais quand exactement ?

			// Est-ce qu'il y a eu un départ vers cette destination ? Et est ce qu'on a des restriction pour cette destination?
			if( lastDepartureDateDest != null && timeBetweenVehicleDest != -1) {
				// Il faut donc vérifier que l'on va respecter ces restrictions supplémentaires
				// Premier cas : minimalDepartureDate est de toute façon après toutes les dates possibles en rajoutant les contraintes de délais
				if(lastDepartureDate.plusMillis(timeBetweenVehicle).isSmallerThan(minimalDepartureDate, false) && lastDepartureDateDest.plusMillis(timeBetweenVehicleDest).isSmallerThan(minimalDepartureDate, false)) {
					return minimalDepartureDate;
				}
				// Deuxième cas : on doit respecter la contrainte de temps de cette ligne spécifiquement
				if( lastDepartureDate.plusMillis(timeBetweenVehicle).isSmallerThan(lastDepartureDateDest.plusMillis(timeBetweenVehicleDest), false) ) {
					return lastDepartureDateDest.plusMillis(timeBetweenVehicleDest);
				}
				// Troisième cas : on doit respecter la contrainte de temps pour ce mode de transport spécifiquement
				// lastDepartureDateDest.plusMillis(timeBetweenVehicleDest).isSmallerThan(lastDepartureDate.plusMillis(timeBetweenVehicle), false)
				return lastDepartureDate.plusMillis(timeBetweenVehicle);
			}
			else {
				// Non, alors, on part dès que possible comme dans un cas classique (soit minimalDepartureDate si on peut, et lastDepartureDate.plusMillis(timeBetweenVehicle si non)
				if(lastDepartureDate.plusMillis(timeBetweenVehicle).isSmallerThan(minimalDepartureDate, false) && lastDepartureDateDest != null && lastDepartureDateDest.plusMillis(timeBetweenVehicleDest).isSmallerThan(minimalDepartureDate, false)) {
					return minimalDepartureDate;
				}
				return lastDepartureDate.plusMillis(timeBetweenVehicle);
			}
		}

		// Il n'y a jamais eu de départ, mais il y en a de prévu prochainement (lastDepartureDate n'est renseigné qu'au moment du départ effectif du véhicule)
		// peut-on envoyé la marchandise AVANT le prochain véhicule ?
		if(lastDepartureDate == null && 0 <= vehicles.size()) {
			IAgent v = vehicles.get(0);
			// On commence par vérifier que la contrainte "souple" soit respectée
			if(minimalDepartureDate.plusMillis(timeBetweenVehicle).isSmallerThan(((GamaDate)v.getAttribute("departureDate")), false)) {

				// Et ensuite, on vérifie si on doit en plus respecter la contrainte supplémentaire sur cette ligne

				// Il n'y a jamais eu de départ (donc pas non plus vers cette destination), par contre est ce qu'on a des restrictions pour cette destination ?
				if( timeBetweenVehicleDest != -1) {
					// si tel est le cas, vérifier si on respecte les délais avec tous les prochains véhicules partent au même endroit (pas seulement le premier prévu)
					boolean dateOk = true; // Par défaut, on suppose que l'on va respecter la contrainte
					for(IAgent vehicle : vehicles) {
						if(vehicle.getAttribute("destination") == dest) {
							if(minimalDepartureDate.plusMillis(timeBetweenVehicleDest).isGreaterThan(((GamaDate)v.getAttribute("departureDate")), false)) {
								// Il existe un véhicule déjà prévu, pour lequel on a un conflit avec la date minimal
								// Il faudra donc trouver une date plus tard et non avant le premier véhicule
								dateOk = false;
							}
						}
					}
					if(dateOk) {
						// On n'a pas pu trouver de conflit entre notre date minimal et un éventuel autre véhicule allant au même endroit.
						return minimalDepartureDate;
					}
				}
				else {
					// on peut partir ASAP
					return minimalDepartureDate;
				}
			}
		}

		// Il y a déjà eu un départ, et il y en a d'autres de prévus. Peut-on caser notre départ avant le premier véhicule déjà prévu?
		if(lastDepartureDate != null  && 0 <= vehicles.size()) {
			GamaDate dv0 = ((GamaDate)vehicles.get(0).getAttribute("departureDate"));
			// Dans tous les cas (minimalDepartureDate + timeBetweenVehicle) doit être avant dv0
			if(dv0.isGreaterThan(minimalDepartureDate.plusMillis(timeBetweenVehicle), false)) {
				// On est dans le cas où la date de départ final pourrait se trouver entre lastDepartureDate et dv0
				// Le mieux serait que la date de départ soit minimalDepartureDate
				// mais c'est à condition que ça respecte les contraintes de timeBetweenVehicle (et timeBetweenVehicleDest si elle existe)
				if(minimalDepartureDate.isGreaterThan(lastDepartureDate.plusMillis(timeBetweenVehicle), false) ) {
					// Vérifier en plus la contrainte "lastDepartureDateDest"
					if(lastDepartureDateDest != null && timeBetweenVehicleDest != -1) {
						// Est ce que la contrainte avec le précédent départ pour ce départ est respecté ?
						if(minimalDepartureDate.isGreaterThan(lastDepartureDateDest.plusMillis(timeBetweenVehicleDest), false) ) {
							// si c'est toujours ok, alors il faut aussi vérifier la contrainte avec le prochain départ vers cette destination (s'il y en a un)
							boolean dateOk = true; // Par défaut, on suppose que l'on va respecter la contrainte
							for(IAgent vehicle : vehicles) {
								if(vehicle.getAttribute("destination") == dest) {
									if( minimalDepartureDate.plusMillis(timeBetweenVehicleDest).isGreaterThan(((GamaDate)vehicle.getAttribute("departureDate")), false) ) {
										// Il existe un véhicule déjà prévu vers cette destination, pour lequel on a un conflit avec la date de départ correspondant à lastDepartureDateDest + timeBetweenVehicleDest
										// Il faudra donc trouver une date plus tard et non avant le premier véhicule
										dateOk = false;
									}
								}
							}
							if(dateOk) {
								return minimalDepartureDate;
							}
						}
					}
					else {
						return minimalDepartureDate;
					}
				}
				// Si les conditions ne sont pas respectées, alors le dernier recours serait que la date de départ soit lastDepartureDate + timeBetweenVehicle (dans le cas sans contrainte associé à la destination, sinon voir ci-après)
				// Sinon la date de départ sera forcément après dv0
				else if( lastDepartureDate.plusMillis(timeBetweenVehicle*2).isSmallerThan(dv0, false) ){ // timeBetweenVehicle*2 car on doit respecter les délais avant lastDepartureDate et avant dv0
					// Vérifier en plus la contrainte "lastDepartureDateDest"
					if(lastDepartureDateDest != null && timeBetweenVehicleDest != -1) {
						// Dans ce cas, on comence par vérifier que l'on peut respecter la contrainte avec le départ précédent (vers cette dest) et également le prochain départ (pas nécessairement vers cette dest)
						if(lastDepartureDateDest.plusMillis(timeBetweenVehicleDest+timeBetweenVehicle).isSmallerThan(dv0, false)) {
							// si c'est toujours ok, alors il faut vérifier la contrainte avec le prochain départ vers cette destination (s'il y en a un)
							boolean dateOk = true; // Par défaut, on suppose que l'on va respecter la contrainte
							for(IAgent vehicle : vehicles) {
								if(vehicle.getAttribute("destination") == dest) {
									if( lastDepartureDateDest.plusMillis(timeBetweenVehicleDest*2).isGreaterThan(((GamaDate)vehicle.getAttribute("departureDate")), false) ) {
										// Il existe un véhicule déjà prévu vers cette destination, pour lequel on a un conflit avec la date de départ correspondant à lastDepartureDateDest + timeBetweenVehicleDest
										// Il faudra donc trouver une date plus tard et non avant le premier véhicule
										dateOk = false;
									}
								}
							}
							if(dateOk) {
								return lastDepartureDateDest.plusMillis(timeBetweenVehicleDest);
							}
						}
					}
					else {
						// Le cas où on n'a pas de contrainte supplémentaire sur la destination
						return lastDepartureDate.plusMillis(timeBetweenVehicle);
					}
				}
			}
		}

		// Si on a encore rien retourné, alors, on doit retourner une date nécessairement après la date de départ du premier véhicule
		// 1) Soit il s'agit d'une date "coincée" entre deux véhicules prévus au départ
		GamaDate lddd = lastDepartureDateDest;
		for(int i = 0; i < vehicles.size() - 1; i ++) {
			IAgent v2 = (IAgent) vehicles.get(i + 1);
			GamaDate d2 = ((GamaDate) v2.getAttribute("departureDate"));
			if(v2.getAttribute("destination") == dest) {
				lddd = d2;
			}
			// Dans tous les cas (minimalDepartureDate + timeBetweenVehicle) doit être avant d2
			if(d2.isGreaterThan(minimalDepartureDate.plusMillis(timeBetweenVehicle), false)) {
				// On est dans le cas où la date de départ final pourrait se trouver entre d1 et d2
				IAgent v1 = (IAgent) vehicles.get(i);
				GamaDate d1 = ((GamaDate) v1.getAttribute("departureDate"));
				// Le mieux serait que la date de départ soit minimalDepartureDate
				// mais c'est à condition que ça respecte les contraintes de timeBetweenVehicle
				if(minimalDepartureDate.isGreaterThan(d1.plusMillis(timeBetweenVehicle), false) ) {
					// Vérifier en plus la contrainte "lastDepartureDateDest"
					if(lastDepartureDateDest != null && timeBetweenVehicleDest != -1) {
						// Est ce que la contrainte avec le précédent départ pour cette destination est respectée ?
						if(minimalDepartureDate.isGreaterThan(lddd.plusMillis(timeBetweenVehicleDest), false) ) {
							// si c'est toujours ok, alors il faut aussi vérifier la contrainte avec le prochain départ vers cette destination (s'il y en a un)
							boolean dateOk = true; // Par défaut, on suppose que l'on va respecter la contrainte
							for(int j = i+1; j < vehicles.size(); j ++) {
								IAgent v3 = (IAgent) vehicles.get(j);
								if(v3.getAttribute("destination") == dest) {
									if( // minimalDepartureDate.isSmallerThan(((GamaDate)v3.getAttribute("departureDate")), false) && // => inutile si on parcours pas toute la liste mais qu'on commence à l'indice i+1
											minimalDepartureDate.plusMillis(timeBetweenVehicleDest).isGreaterThan(((GamaDate)v3.getAttribute("departureDate")), false) ) {
										// Il existe un véhicule déjà prévu vers cette destination, pour lequel on a un conflit avec la date de départ correspondant à lastDepartureDateDest + timeBetweenVehicleDest
										// Il faudra donc trouver une date plus tard et non avant le premier véhicule
										dateOk = false;
									}
								}
							}
							if(dateOk) {
								return minimalDepartureDate;
							}
						}
					}
					else {
						return minimalDepartureDate;
					}
				}
				// Si les conditions ne sont pas respectées, alors une solution serait que la date de départ soit d1.plusMillis(timeBetweenVehicle). Sinon la date de départ sera forcément après d2
				// De plus, s'il existe une contrainte avec la destination, la solution pourrait être lastDepartureDateDest + timeBetweenVehicleDest
				else if( d1.plusMillis(timeBetweenVehicle*2).isSmallerThan(d2, false) ){
					// Vérifier en plus la contrainte "lastDepartureDateDest"
					if(lastDepartureDateDest != null && timeBetweenVehicleDest != -1) {
						// Est ce que la contrainte avec le précédent départ pour ce départ est respecté ?
						if((d1.plusMillis(timeBetweenVehicle)).isGreaterThan(lddd.plusMillis(timeBetweenVehicleDest), false) ) {
							// si c'est toujours ok, alors il faut aussi vérifier la contrainte avec le prochain départ vers cette destination (s'il y en a un)
							boolean dateOk = true; // Par défaut, on suppose que l'on va respecter la contrainte
							for(int j = i+1; j < vehicles.size(); j ++) {
								IAgent v3 = (IAgent) vehicles.get(j);
								if(v3.getAttribute("destination") == dest) {
									if( minimalDepartureDate.plusMillis(timeBetweenVehicleDest).isGreaterThan(((GamaDate)v3.getAttribute("departureDate")), false) ) {
										// Il existe un véhicule déjà prévu vers cette destination, pour lequel on a un conflit avec la date de départ correspondant à lastDepartureDateDest + timeBetweenVehicleDest
										// Il faudra donc trouver une date plus tard et non avant le premier véhicule
										dateOk = false;
									}
								}
							}
							if(dateOk) {
								return d1.plusMillis(timeBetweenVehicle);
							}
						}
						else if( (lddd.plusMillis(timeBetweenVehicleDest)).isGreaterThan(d1.plusMillis(timeBetweenVehicle), false) &&
								(lddd.plusMillis(timeBetweenVehicleDest+timeBetweenVehicle)).isSmallerThan(d2, false)){
							GamaDate d = lddd.plusMillis(timeBetweenVehicleDest);
							// si c'est toujours ok, alors il faut aussi vérifier la contrainte avec le prochain départ vers cette destination (s'il y en a un)
							boolean dateOk = true; // Par défaut, on suppose que l'on va respecter la contrainte
							for(int j = i+1; j < vehicles.size(); j ++) {
								IAgent v3 = (IAgent) vehicles.get(j);
								if(v3.getAttribute("destination") == dest) {
									if( d.plusMillis(timeBetweenVehicleDest).isGreaterThan(((GamaDate)v3.getAttribute("departureDate")), false) ) {
										// Il existe un véhicule déjà prévu vers cette destination, pour lequel on a un conflit avec la date de départ correspondant à lastDepartureDateDest + timeBetweenVehicleDest
										// Il faudra donc trouver une date plus tard et non avant le premier véhicule
										dateOk = false;
									}
								}
							}
							if(dateOk) {
								return d;
							}
						}
					}
					else {
						return d1.plusMillis(timeBetweenVehicle);
					}
				}
			}
		}

		// 2) soit il s'agit d'une date après le dernier véhicule
		GamaDate lastDate = (GamaDate) vehicles.get(vehicles.size()-1).getAttribute("departureDate");
		lddd = lastDepartureDateDest;
		for(int i = 0; i < vehicles.size() - 1; i ++) {
			IAgent v = (IAgent) vehicles.get(i + 1);
			GamaDate d = ((GamaDate) v.getAttribute("departureDate"));
			if(v.getAttribute("destination") == dest) {
				lddd = d;
			}
		}
		if( minimalDepartureDate.isGreaterThan(lastDate.plusMillis(timeBetweenVehicle), false) ) {
			// Vérifier en plus la contrainte "lastDepartureDateDest"
			if(lddd != null && timeBetweenVehicleDest != -1) {
				// Est ce que la contrainte avec le précédent départ pour cette destination est respectée ?
				if(minimalDepartureDate.isSmallerThan(lddd.plusMillis(timeBetweenVehicleDest), false) ) {
					return lddd.plusMillis(timeBetweenVehicleDest);
				}
			}
			return minimalDepartureDate;
		}
		else {
			// Vérifier en plus la contrainte "lastDepartureDateDest"
			if(lddd != null && timeBetweenVehicleDest != -1) {
				// Est ce que la contrainte avec le précédent départ pour cette destination est respectée ?
				if(lastDate.plusMillis(timeBetweenVehicle).isSmallerThan(lddd.plusMillis(timeBetweenVehicleDest), false) ) {
					return lddd.plusMillis(timeBetweenVehicleDest);
				}
			}
			return lastDate.plusMillis(timeBetweenVehicle);
		}
	}

	private static IAgent createVehicle(final IScope scope, IAgent transporter, IAgent commodity, Node currentNode, Node destination) {
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

	/**
	 * This method returns the time between vehicles that should be considered for a specified network and a given origin-destination.
	 * The result is returned according to the attribute "mapParameters" declared as a map in the GAMA model.
	 * This map is built from a JSON file which should look like this :
	 *
	 * {
	 * 		"id_terminale_origin" : {
	 * 			"id_terminale_dest" : {
	 * 				"handling_time_maritime":4,
	 * 				"handling_time_river":3,
	 * 			}
	 * 		}
	 * }
	 *
	 * @param scope
	 * @param transporter
	 * @param source
	 * @param dest
	 * @return
	 */
	protected static Double getTimeBeweenVehicleDest(final IScope scope, IAgent transporter, IAgent source, IAgent dest) {
		GamaMap mapParameters = (GamaMap) scope.getSimulation().getAttribute("mapParameters");
		Double timeBetweenVehicles = null;
		String sourceName = (String) source.getAttribute("cityName");
		String destName = (String) dest.getAttribute("cityName");

		if(mapParameters.containsKey(sourceName)) {
			if( ((GamaMap)mapParameters.get(sourceName)).containsKey(destName) ){
				if( ((GamaMap)((GamaMap)mapParameters.get(sourceName)).get(destName))
					.containsKey("timeBetweenVehicles_"+(String)transporter.getAttribute("networkType")) ){

					timeBetweenVehicles = ((BigDecimal)(((GamaMap)((GamaMap)mapParameters.get(sourceName)).get(destName))
							.get("timeBetweenVehicles_"+(String)transporter.getAttribute("networkType")))).doubleValue();

					if(timeBetweenVehicles == -1) {
						timeBetweenVehicles = Double.POSITIVE_INFINITY;
					}
				}
			}
		}
		if(timeBetweenVehicles == null)
			return new Double(-1);
		return timeBetweenVehicles;
	}
}
