package skill;

import java.time.temporal.ChronoUnit;

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
			Node source, Node dest, GamaDate minimalDepartureDate, double volume) {
		// We get the gama agent associated to the GS node
		IAgent buildingSource = source.getAttribute("gama_agent");
		IAgent buildingDest = dest.getAttribute("gama_agent");
		// Then we get the list of leaving vehicle of this node
		IList vehicles = (IList) buildingSource.getAttribute("leavingVehicles_"+(String)transporter.getAttribute("networkType"));

		// Then, we browse these vehicles,
		// and we try to find a vehicle which can carry the volume of goods and whose the departure date is OK (meaning: we have the time to handle the goods before the vehicles actually leaves)
		// and whose the destination is the same
		IAgent vehicle = null;
		boolean notfound = true;
		for(int i = 0; i < vehicles.size() && notfound; i++){
			IAgent currentVehicle = (IAgent) vehicles.get(i);
			if((IAgent) currentVehicle.getAttribute("destination") == buildingDest){
				if(((double) currentVehicle.getAttribute("scheduledTransportedVolume") + volume) <= (double) transporter.getAttribute("maximalTransportedVolume")){
					GamaDate departureDate = (GamaDate) currentVehicle.getAttribute("departureDate");
					if(departureDate.isGreaterThan(minimalDepartureDate, false)){
						vehicle = currentVehicle;
						notfound = false;
					}
				}
			}
		}
		GamaDate returnedDate;
		// If we did not find a vehicle, we do as if we create a new one
		if(vehicle == null){
			// Two cases why we did not find a vehicle :
			// 1) The list is empty
			if(vehicles.size() == 0){
				// the departure date of the next vehicle is :
				// the greater date between :
					// - the current date + the handling time (= minimalDepartureDate)
					// => it is equal to the "minimalDepartureDate
					// - the previous vehicle departure + the minimal time between two vehicles
				GamaDate lastVehicleDeparture = ((GamaDate)buildingSource.getAttribute("lastVehicleDeparture_"+(String)transporter.getAttribute("networkType")));
				if(lastVehicleDeparture != null) {
					GamaDate date = lastVehicleDeparture
							.plusMillis((double)transporter.getAttribute("timeBetweenVehicles")*3600*1000);
					if(minimalDepartureDate.isGreaterThan(date, false))
						returnedDate = minimalDepartureDate;
					else
						returnedDate = date;
				}
				else {
					returnedDate = minimalDepartureDate;
				}
			}
			else {
				// 2) There is at least one vehicle but we can not use it (it is full or we don't have time to handle the goods before it leaves)
				// the departure date of the next vehicle is :
					// the departure date of the last vehicle + the minimal time between two vehicles
				returnedDate = ((GamaDate)buildingSource.getAttribute("lastVehicleDeparture_"+(String)transporter.getAttribute("networkType")))
						.plusMillis((double)transporter.getAttribute("timeBetweenVehicles")*3600*1000);
			}
		}
		else {
			// Else, we return the departure date of the found vehicle
			returnedDate = (GamaDate) vehicle.getAttribute("departureDate");
		}
		roundDate(scope, returnedDate);
		return returnedDate;
	}

	public static void registerDepartureDate(final IScope scope, IAgent transporter, IAgent commodity, Node currentNode, Node destination, GamaDate minimalDepartureDate) {
		// We get the gama agent associated to the GS node
		IAgent building = currentNode.getAttribute("gama_agent");
		// Then we get the list of leaving vehicle of this node
		IList vehicles = (IList) building.getAttribute("leavingVehicles_"+(String)transporter.getAttribute("networkType"));
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
			vehicle = scope.getSimulation().getPopulationFor("Vehicle").createAgents(scope, 1, null, false, false).get(0);
			vehicle.setLocation(building.getLocation());
			vehicle.setAttribute("scheduledTransportedVolume", ((double) commodity.getAttribute("volume")));
			IList scheduledCommodities = ((IList)vehicle.getAttribute("scheduledCommodities"));
			scheduledCommodities.add(commodity);
			vehicle.setAttribute("scheduledCommodities", scheduledCommodities);
			vehicle.setAttribute("destination", destination.getAttribute("gama_agent"));
			vehicle.setAttribute("source", currentNode.getAttribute("gama_agent"));
			vehicle.setAttribute("networkType", transporter.getAttribute("networkType"));
			// Two cases why we did not find a vehicle :
			// The list is empty
			if(vehicles.size() == 0){
				// the departure date of the next vehicle is :
				// the greater date between :
					// - the current date + the handling time (= minimalDepartureDate)
					// - the previous vehicle departure + the minimal time between two vehicles
				GamaDate lastVehicleDeparture = ((GamaDate)building.getAttribute("lastVehicleDeparture_"+(String)transporter.getAttribute("networkType")));
				if(lastVehicleDeparture != null) {
					GamaDate date = roundDate(scope, lastVehicleDeparture
							.plusMillis((double)transporter.getAttribute("timeBetweenVehicles")*3600*1000));

					if(minimalDepartureDate.isGreaterThan(date, false)) {
						vehicle.setAttribute("departureDate", minimalDepartureDate);
						building.setAttribute("lastVehicleDeparture_"+(String)transporter.getAttribute("networkType"), minimalDepartureDate);
					}
					else {
						vehicle.setAttribute("departureDate", date);
						building.setAttribute("lastVehicleDeparture_"+(String)transporter.getAttribute("networkType"), date);
					}
				}
				else {
					vehicle.setAttribute("departureDate", minimalDepartureDate);
					building.setAttribute("lastVehicleDeparture_"+(String)transporter.getAttribute("networkType"), minimalDepartureDate);
				}
			}
			else {
				// There is at least one vehicle but we can not use it (it is full or we don't have time to handle the goods before it leaves)
					// the departure date of the next vehicle is :
						// the departure date of the last vehicle + the minimal time between two vehicles
				GamaDate date = roundDate(scope, ((GamaDate)building.getAttribute("lastVehicleDeparture_"+(String)transporter.getAttribute("networkType")))
						.plusMillis((double)transporter.getAttribute("timeBetweenVehicles")*3600*1000));
				vehicle.setAttribute("departureDate",  date);
				building.setAttribute("lastVehicleDeparture_"+(String)transporter.getAttribute("networkType"), date);
			}
			vehicles.add(vehicle);
		}
		else {
			// We use the found vehicle
			// We add the commodity to the transported commodities
			vehicle.setAttribute("scheduledTransportedVolume",
					((double) vehicle.getAttribute("scheduledTransportedVolume")) + ((double) commodity.getAttribute("volume")));
			IList scheduledCommodities = ((IList)vehicle.getAttribute("scheduledCommodities"));
			scheduledCommodities.add(commodity);
			vehicle.setAttribute("scheduledCommodities", scheduledCommodities);
		}
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
