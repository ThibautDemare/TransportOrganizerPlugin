package skill;

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
	
	public static GamaDate getDepartureDate(final IScope scope, IAgent transporter, 
			Node source, Node dest, GamaDate minimalDepartureDate, double volume) {
		// We get the gama agent associated to the GS node
		IAgent buildingSource = source.getAttribute("gama_agent");
		IAgent buildingDest = dest.getAttribute("gama_agent");
		// Then we get the list of leaving vehicle of this node
		IList vehicles = (IList) buildingSource.getAttribute("leavingVehicles");

		// Then, we browse these vehicles,
		// and we try to find a vehicle which can carry the volume of goods and whose the departure date is OK (meaning: we have the time to handle the goods before the vehicles actually leaves)
		// and whose the destination is the same
		IAgent vehicle = null;
		boolean notfound = true;
		for(int i = 0; i < vehicles.size() && notfound; i++){
			IAgent currentVehicle = (IAgent) vehicles.get(i);
			if((IAgent) currentVehicle.getAttribute("destination") == buildingDest){
				if(((double) currentVehicle.getAttribute("currentTransportedVolume") + volume) <= (double) transporter.getAttribute("maximalTransportedVolume")){
					GamaDate departureDate = (GamaDate) currentVehicle.getAttribute("departureDate");
					if(departureDate.isGreaterThan(minimalDepartureDate, false)){
						vehicle = currentVehicle;
						notfound = false;
					}
				}
			}
		}
		// If we did not find a vehicle, we do as if we create a new one
		if(vehicle == null){
			// Two cases why we did not find a vehicle :
			// 1) The list is empty
			if(vehicles.size() == 0){
				// the departure date of the next vehicle is :
				// the greater date between :
					// - the current date + the handling time
					// => it is equal to the "minimalDepartureDate
					// - the current date + the minimal time between two vehicles
				GamaDate date = minimalDepartureDate
						.plusMillis((double)transporter.getAttribute("timeBetweenVehicles")*3600*1000);
				if(minimalDepartureDate.isGreaterThan(date, false))
					return minimalDepartureDate;
				else
					return date;
			}
			// 2) There is at least one vehicle but we can not use it (it is full or we don't have time to handle the goods before it leaves)
				// the departure date of the next vehicle is :
					// the departure date of the last vehicle + the minimal time between two vehicles
			return ((GamaDate)((IAgent) vehicles.get(vehicles.size()-1)).getAttribute("departureDate"))
					.plusMillis((double)transporter.getAttribute("timeBetweenVehicles")*3600*1000);
		}
		// Else, we return the departure date of the found vehicle
		return (GamaDate) vehicle.getAttribute("departureDate");
	}
	
	public static void registerDepartureDate(final IScope scope, IAgent transporter, IAgent commodity, Node currentNode, Node destination, GamaDate minimalDepartureDate) {
		// We get the gama agent associated to the GS node
		IAgent building = currentNode.getAttribute("gama_agent");
		// Then we get the list of leaving vehicle of this node
		IList vehicles = (IList) building.getAttribute("leavingVehicles");
		// Then, we browse these vehicles,
		// and we try to find a vehicle which can carry the volume of goods and whose the departure date is OK (meaning: we have the time to handle the goods before the vehicles actually leaves)
		IAgent vehicle = null;
		boolean notfound = true;
		for(int i = 0; i < vehicles.size() && notfound; i++){
			IAgent currentVehicle = (IAgent) vehicles.get(i);
			if(destination.getAttribute("gama_agent") == vehicle.getAttribute("destination")){
				if(((double) currentVehicle.getAttribute("currentTransportedVolume") + (double)commodity.getAttribute("volume")) <= (double) transporter.getAttribute("maximalTransportedVolume")){
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
			vehicle.setAttribute("currentTransportedVolume", 
					((double) vehicle.getAttribute("currentTransportedVolume")) + ((double) commodity.getAttribute("volume")));
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
					// - the current date + the handling time
					// => it is equal to the "minimalDepartureDate
					// - the current date + the minimal time between two vehicles
				GamaDate date = minimalDepartureDate
						.plusMillis((double)transporter.getAttribute("timeBetweenVehicles")*3600*1000);
				if(minimalDepartureDate.isGreaterThan(date, false))
					vehicle.setAttribute("departureDate", minimalDepartureDate);
				else
					vehicle.setAttribute("departureDate", date);
				return;
			}
			else {
				// There is at least one vehicle but we can not use it (it is full or we don't have time to handle the goods before it leaves)
					// the departure date of the next vehicle is :
						// the departure date of the last vehicle + the minimal time between two vehicles
				vehicle.setAttribute("departureDate",  ((GamaDate)((IAgent) vehicles.get(vehicles.size()-1)).getAttribute("departureDate"))
						.plusMillis((double)transporter.getAttribute("timeBetweenVehicles")*3600*1000));
			}
		}
		else {
			// We use the found vehicle
			// We add the commodity to the transported commodities
			vehicle.setAttribute("currentTransportedVolume", 
					((double) vehicle.getAttribute("currentTransportedVolume")) + ((double) commodity.getAttribute("volume")));
			IList scheduledCommodities = ((IList)vehicle.getAttribute("scheduledCommodities"));
			scheduledCommodities.add(commodity);
			vehicle.setAttribute("scheduledCommodities", scheduledCommodities);
		}
	}
}
