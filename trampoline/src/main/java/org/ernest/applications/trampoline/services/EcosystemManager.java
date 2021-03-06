package org.ernest.applications.trampoline.services;

import java.util.UUID;
import java.util.stream.Collectors;

import org.ernest.applications.trampoline.entities.Ecosystem;
import org.ernest.applications.trampoline.entities.Instance;
import org.ernest.applications.trampoline.entities.Microservice;
import org.ernest.applications.trampoline.entities.StatusInstance;
import org.ernest.applications.trampoline.exceptions.CreatingMicroserviceScriptException;
import org.ernest.applications.trampoline.exceptions.CreatingSettingsFolderException;
import org.ernest.applications.trampoline.exceptions.ReadingEcosystemException;
import org.ernest.applications.trampoline.exceptions.RunningMicroserviceScriptException;
import org.ernest.applications.trampoline.exceptions.SavingEcosystemException;
import org.ernest.applications.trampoline.exceptions.ShuttingDownInstanceException;
import org.jboss.resteasy.client.ClientRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcosystemManager {
	
	@Autowired
	FileManager fileManager;
	
	public Ecosystem getEcosystem() throws CreatingSettingsFolderException, ReadingEcosystemException {
		return fileManager.getEcosystem();
	}
	
	public void setMavenLocation(String path) throws CreatingSettingsFolderException, ReadingEcosystemException, SavingEcosystemException {
		Ecosystem ecosystem = fileManager.getEcosystem();
		ecosystem.setMavenLocation(path);
		fileManager.saveEcosystem(ecosystem);
	}
	
	public void setNewMicroservice(String name, String pomLocation, String defaultPort) throws CreatingSettingsFolderException, ReadingEcosystemException, CreatingMicroserviceScriptException, SavingEcosystemException {
		Ecosystem ecosystem = fileManager.getEcosystem();
		
		Microservice microservice = new Microservice();
		microservice.setId(UUID.randomUUID().toString());
		microservice.setName(name);
		microservice.setPomLocation(pomLocation);
		microservice.setDefaultPort(defaultPort);
		fileManager.createScript(microservice.getId(), pomLocation);
		
		ecosystem.getMicroservices().add(microservice);
		fileManager.saveEcosystem(ecosystem);
	}
	
	public void removeMicroservice(String id) throws CreatingSettingsFolderException, ReadingEcosystemException, SavingEcosystemException {
		Ecosystem ecosystem = fileManager.getEcosystem();
		ecosystem.setMicroservices(ecosystem.getMicroservices().stream().filter(m -> !m.getId().equals(id)).collect(Collectors.toList()));	
		fileManager.saveEcosystem(ecosystem);
	}

	public void startInstance(String id, String port) throws CreatingSettingsFolderException, ReadingEcosystemException, RunningMicroserviceScriptException, SavingEcosystemException{
		Ecosystem ecosystem = fileManager.getEcosystem();
		
		Microservice microservice = ecosystem.getMicroservices().stream().filter(m -> m.getId().equals(id)).collect(Collectors.toList()).get(0);
		fileManager.runScript(microservice.getId(), ecosystem.getMavenLocation(), port);
		
		Instance instance = new Instance();
		instance.setId(UUID.randomUUID().toString());
		instance.setPort(port);
		instance.setName(microservice.getName());
		instance.setPomLocation(microservice.getPomLocation());
		ecosystem.getInstances().add(instance);
		fileManager.saveEcosystem(ecosystem);
	}

	public void killInstance(String id) throws CreatingSettingsFolderException, ReadingEcosystemException, SavingEcosystemException, ShuttingDownInstanceException {
		Ecosystem ecosystem = fileManager.getEcosystem();
		Instance instance = ecosystem.getInstances().stream().filter(i -> i.getId().equals(id)).collect(Collectors.toList()).get(0);
		
		try {
			new ClientRequest("http://localhost:" + instance.getPort() + "/shutdown").post(String.class);
		} catch (Exception e) {
			e.printStackTrace();
			throw new ShuttingDownInstanceException();
		}
		
		ecosystem.setInstances(ecosystem.getInstances().stream().filter(i -> !i.getId().equals(id)).collect(Collectors.toList()));
		fileManager.saveEcosystem(ecosystem);
	}

	public String getStatusInstance(String id) throws CreatingSettingsFolderException, ReadingEcosystemException {
		Ecosystem ecosystem = fileManager.getEcosystem();
		Instance instance = ecosystem.getInstances().stream().filter(i -> i.getId().equals(id)).collect(Collectors.toList()).get(0);
		if(isDeployed(instance)){
			return StatusInstance.DEPLOYED.getCode();
		}
		return StatusInstance.NOT_DEPLOYED.getCode();
	}

	public void removeNotDeployedInstances() throws CreatingSettingsFolderException, ReadingEcosystemException, SavingEcosystemException {
		Ecosystem ecosystem = fileManager.getEcosystem();
		ecosystem.setInstances(ecosystem.getInstances().stream().filter(i -> isDeployed(i)).collect(Collectors.toList()));
		fileManager.saveEcosystem(ecosystem);
	}

	private boolean isDeployed(Instance instance) {
		try{
			new ClientRequest("http://localhost:" + instance.getPort() + "/env").get(String.class);
		}catch(Exception e){
			return false;
		}
		return true;
	}
}