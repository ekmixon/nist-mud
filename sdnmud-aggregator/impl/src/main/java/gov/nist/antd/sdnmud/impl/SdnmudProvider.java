/*
 * Copyright © 2017 Public Domain.
 *
 * This file includes code developed by employees of the National Institute of
 * Standards and Technology (NIST)
 *
 * This software was developed by employees of the National Institute of
 * Standards and Technology (NIST), and others. This software has been
 * contributed to the public domain. Pursuant to title 15 Untied States
 * Code Section 105, works of NIST employees are not subject to copyright
 * protection in the United States and are considered to be in the public
 * domain. As a result, a formal license is not needed to use this software.
 *
 * This software is provided "AS IS." NIST MAKES NO WARRANTY OF ANY KIND,
 * EXPRESS, IMPLIED OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE
 * IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * NON-INFRINGEMENT AND DATA ACCURACY. NIST does not warrant or make any
 * representations regarding the use of the software or the results thereof,
 * including but not limited to the correctness, accuracy, reliability or
 * usefulness of this software.
 */
package gov.nist.antd.sdnmud.impl;

import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.RpcRegistration;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.Acls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev190304.acls.acl.Aces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.Mud;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.controllerclass.mapping.rev170915.ControllerclassMapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.controllerclass.mapping.rev170915.controllerclass.mapping.Controller;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.Mapping;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.mud.device.association.rev170915.QuarantineDevice;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.OpendaylightFlowStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.OpendaylightDirectStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.OpendaylightInventoryListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.meter.service.rev130918.SalMeterService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflow.protocol.rev130731.OpenflowProtocolService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.nist.mud.file.cache.rev170915.MudCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.SdnmudConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.SdnmudService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.service.rev131026.SalTableService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;

public class SdnmudProvider {

	private static final Logger LOG = LoggerFactory.getLogger(SdnmudProvider.class);

	private final DataBroker dataBroker;

	private SalFlowService flowService;

	private PacketProcessingService packetProcessingService;

	private NotificationService notificationService;

	private ListenerRegistration<WakeupOnFlowCapableNode> dataTreeChangeListenerRegistration;

	private AclDataStoreListener aclDataStoreListener;

	private MappingDataStoreListener mappingDataStoreListener;

	private MudProfileDataStoreListener mudProfileDataStoreListener;

	private ControllerclassMappingDataStoreListener controllerClassMappingDataStoreListener;

	private Map<String, Mud> uriToMudMap = new HashMap<String, Mud>();

	// Stores a set of NodeIds for a given mac address (identifies the switches
	// that have seen the mac addr).
	private HashMap<MacAddress, HashSet<String>> macToNodeIdMap = new HashMap<>();

	// Stores a map between node ID and its InstanceIdentifier<FlowCapableNode>
	private HashMap<String, InstanceIdentifier<FlowCapableNode>> uriToNodeMap = new HashMap<>();

	// Map between the node URI and the mud uri.
	private HashMap<String, List<Uri>> nodeToMudUriMap = new HashMap<>();

	// Aces name to ace map.
	private HashMap<String, Aces> nameToAcesMap = new HashMap<String, Aces>();

	// A map between a mac address and the associated FlowCapableNodes where MUD
	// profiles were installed.
	// This is used to retrieve a set of nodes where MUD rules have been
	// installed for a given MAC address.
	private HashMap<String, HashSet<InstanceIdentifier<FlowCapableNode>>> mudNodesMap = new HashMap<>();

	private HashMap<String, HashMap<String, List<Ipv4Address>>> controllerMap = new HashMap<>();

	// private HashSet<String> routerMacAddresses = new HashSet<String>();

	private FlowCommitWrapper flowCommitWrapper;

	private WakeupOnFlowCapableNode wakeupListener;

	private DOMDataBroker domDataBroker;

	private SchemaService schemaService;

	private OpendaylightFlowStatisticsService flowStatisticsService;

	private BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer;

	private MudFlowsInstaller mudFlowsInstaller;

	private int configStateChanged = 0;

	private SdnmudConfig sdnmudConfig;

	private PacketInDispatcher packetInDispatcher;

	private RpcProviderRegistry rpcProviderRegistry;

	private FlowWriter flowWriter;

	private StateChangeScanner stateChangeScanner;

	private MudFileFetcher mudFileFetcher;

	private DatastoreUpdater datastoreUpdater;

	private MudCacheDataStoreListener mudCacheDatastoreListener;

	private ListenerRegistration<SdnmudConfigDataStoreListener> configRegistration;

	private ListenerRegistration<MudProfileDataStoreListener> mudProfileRegistration;

	private ListenerRegistration<AclDataStoreListener> aclRegistration;

	private ListenerRegistration<MappingDataStoreListener> mappingRegistration;

	private ListenerRegistration<MudCacheDataStoreListener> mudCacheRegistration;

	private ListenerRegistration<PacketInDispatcher> packetInDispatcherRegistration;

	private RpcRegistration<SdnmudService> sdnmudServiceRegistration;

	private HashSet<String> cpeNodes = new HashSet<String>();

	private HashMap<String, ControllerclassMapping> controllerClassMaps = new HashMap<String, ControllerclassMapping>();

	private NameResolutionCache nameResolutionCache;

	private NotificationPublishService notificationPublishService;

	private QuaranteneDevicesListener quaranteneDevicesListener;

	private ListenerRegistration<QuaranteneDevicesListener> quaranteneDevicesListenerRegistration;

	private OpendaylightDirectStatisticsService directStatisticsService;

	private DOMSchemaService domSchemaService;

	private MudReportSender mudReporter;

	public SdnmudProvider(final DataBroker dataBroker, SdnmudConfig sdnmudConfig, SalFlowService flowService,
			OpendaylightFlowStatisticsService flowStatisticsService,
			OpendaylightDirectStatisticsService directStatisticsService,
			PacketProcessingService packetProcessingService, NotificationService notificationService,
			DOMDataBroker domDataBroker, SchemaService schemaService, DOMSchemaService domSchemaService,
			NotificationPublishService notificationPublishService,
			BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer, RpcProviderRegistry rpcProviderRegistry) {

		LOG.info("SdnMudProvider: SdnMudProvider - init");
		this.dataBroker = dataBroker;
		// this.flowService = flowService;
		this.packetProcessingService = packetProcessingService;
		this.notificationService = notificationService;
		this.notificationPublishService = notificationPublishService;
		this.domDataBroker = domDataBroker;
		this.schemaService = schemaService;
		this.domSchemaService = domSchemaService;
		this.bindingNormalizedNodeSerializer = bindingNormalizedNodeSerializer;
		this.rpcProviderRegistry = rpcProviderRegistry;
		this.flowService = flowService;
		this.flowStatisticsService = flowStatisticsService;
		this.directStatisticsService = directStatisticsService;
		// this.flowStatisticsService =
		// rpcProviderRegistry.getRpcService(OpendaylightFlowStatisticsService.class);
		// this.directStatisticsService =
		// rpcProviderRegistry.getRpcService(OpendaylightDirectStatisticsService.class);
		this.sdnmudConfig = sdnmudConfig;
		Security.addProvider(new BouncyCastleProvider());
		if (sdnmudConfig.getDropRuleTable() < sdnmudConfig.getTableStart() + 4) {
			LOG.error("Drop rule table is incorrectly specified");
			throw new RuntimeException("Error in config file -- please check defaults. Drop rule table is too small.");
		}

	}

	private static InstanceIdentifier<FlowCapableNode> getWildcardPath() {
		return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class);
	}

	private static InstanceIdentifier<Mud> getMudWildCardPath() {
		return InstanceIdentifier.create(Mud.class);
	}

	private static InstanceIdentifier<Acls> getAclWildCardPath() {
		return InstanceIdentifier.create(Acls.class);
	}

	private static InstanceIdentifier<Mapping> getMappingWildCardPath() {
		return InstanceIdentifier.create(Mapping.class);
	}

	private static InstanceIdentifier<ControllerclassMapping> getControllerClassMappingWildCardPath() {
		return InstanceIdentifier.create(ControllerclassMapping.class);
	}

	private static InstanceIdentifier<SdnmudConfig> getConfigWildCardPath() {
		return InstanceIdentifier.create(SdnmudConfig.class);
	}

	private static InstanceIdentifier<MudCache> getMudCacheWildCardPath() {
		return InstanceIdentifier.create(MudCache.class);
	}

	private static InstanceIdentifier<QuarantineDevice> getQuaranteneDevicesWildCardPath() {
		return InstanceIdentifier.create(QuarantineDevice.class);
	}

	/**
	 * Method called when the blueprint container is created.
	 */
	public void init() {
		LOG.info("SdnmudProvider Session Initiated");

		this.flowCommitWrapper = new FlowCommitWrapper(dataBroker);
		this.flowWriter = new FlowWriter(this.flowService);
		this.mudFlowsInstaller = new MudFlowsInstaller(this);
		this.datastoreUpdater = new DatastoreUpdater(this);
		this.nameResolutionCache = new NameResolutionCache();
		/* Listener for flow miss packets sent to the controller */
		this.packetInDispatcher = new PacketInDispatcher(this);

		/* Register listener for configuration state change */
		InstanceIdentifier<SdnmudConfig> configWildCardPath = getConfigWildCardPath();
		final DataTreeIdentifier<SdnmudConfig> configId = new DataTreeIdentifier<SdnmudConfig>(
				LogicalDatastoreType.CONFIGURATION, configWildCardPath);
		SdnmudConfigDataStoreListener configDataStoreListener = new SdnmudConfigDataStoreListener(this);
		this.configRegistration = this.dataBroker.registerDataTreeChangeListener(configId, configDataStoreListener);

		/* Register a data tree change listener for MUD profiles */
		InstanceIdentifier<Mud> mudWildCardPath = getMudWildCardPath();
		final DataTreeIdentifier<Mud> treeId = new DataTreeIdentifier<Mud>(LogicalDatastoreType.CONFIGURATION,
				mudWildCardPath);
		this.mudProfileDataStoreListener = new MudProfileDataStoreListener(dataBroker, this);
		this.mudProfileRegistration = this.dataBroker.registerDataTreeChangeListener(treeId,
				mudProfileDataStoreListener);

		/* Register a data tree change listener for ACL profiles */
		final InstanceIdentifier<Acls> aclWildCardPath = getAclWildCardPath();
		final DataTreeIdentifier<Acls> aclTreeId = new DataTreeIdentifier<Acls>(LogicalDatastoreType.CONFIGURATION,
				aclWildCardPath);
		this.aclDataStoreListener = new AclDataStoreListener(dataBroker, this);
		this.aclRegistration = this.dataBroker.registerDataTreeChangeListener(aclTreeId, aclDataStoreListener);

		/*
		 * Instance of mud file fetcher. Should be set before mapping datastore
		 * registration.
		 */
		this.mudFileFetcher = new MudFileFetcher(this);

		/*
		 * Register a data tree change listener for MAC to MUD URL mapping. The MAC to
		 * URL mapping is provided by the system admin.
		 */
		final InstanceIdentifier<Mapping> mappingWildCardPath = getMappingWildCardPath();
		final DataTreeIdentifier<Mapping> mappingTreeId = new DataTreeIdentifier<Mapping>(
				LogicalDatastoreType.CONFIGURATION, mappingWildCardPath);
		this.mappingDataStoreListener = new MappingDataStoreListener(this);
		this.mappingRegistration = this.dataBroker.registerDataTreeChangeListener(mappingTreeId,
				mappingDataStoreListener);

		/*
		 * Mud cache manager.
		 */
		final InstanceIdentifier<MudCache> mudCacheWildCardPath = getMudCacheWildCardPath();
		final DataTreeIdentifier<MudCache> mudCacheTreeId = new DataTreeIdentifier<MudCache>(
				LogicalDatastoreType.CONFIGURATION, mudCacheWildCardPath);
		this.mudCacheDatastoreListener = new MudCacheDataStoreListener(this);
		this.mudCacheRegistration = this.dataBroker.registerDataTreeChangeListener(mudCacheTreeId,
				mudCacheDatastoreListener);

		final InstanceIdentifier<QuarantineDevice> quaranteneDevicesWildCardPath = getQuaranteneDevicesWildCardPath();
		final DataTreeIdentifier<QuarantineDevice> quaranteneDevicesId = new DataTreeIdentifier<QuarantineDevice>(
				LogicalDatastoreType.CONFIGURATION, quaranteneDevicesWildCardPath);
		this.quaranteneDevicesListener = new QuaranteneDevicesListener(this);
		this.quaranteneDevicesListenerRegistration = this.dataBroker.registerDataTreeChangeListener(quaranteneDevicesId,
				quaranteneDevicesListener);
		this.packetInDispatcherRegistration = this.getNotificationService()
				.registerNotificationListener(packetInDispatcher);

		/*
		 * Register a data tree change listener for Controller Class mapping. A
		 * controller class mapping maps a controller class URI to a list of internet
		 * addresses
		 */
		final InstanceIdentifier<ControllerclassMapping> controllerClassMappingWildCardPath = getControllerClassMappingWildCardPath();
		this.controllerClassMappingDataStoreListener = new ControllerclassMappingDataStoreListener(this);
		final DataTreeIdentifier<ControllerclassMapping> ccmappingTreeId = new DataTreeIdentifier<ControllerclassMapping>(
				LogicalDatastoreType.CONFIGURATION, controllerClassMappingWildCardPath);
		this.dataBroker.registerDataTreeChangeListener(ccmappingTreeId, controllerClassMappingDataStoreListener);

		SdnmudServiceImpl service = new SdnmudServiceImpl(this);
		this.sdnmudServiceRegistration = this.rpcProviderRegistry.addRpcImplementation(SdnmudService.class, service);

		// Create a listener that wakes up on a node being added.
		this.wakeupListener = new WakeupOnFlowCapableNode(this);
		final DataTreeIdentifier<FlowCapableNode> dataTreeIdentifier = new DataTreeIdentifier<FlowCapableNode>(
				LogicalDatastoreType.OPERATIONAL, getWildcardPath());
		this.dataTreeChangeListenerRegistration = this.dataBroker.registerDataTreeChangeListener(dataTreeIdentifier,
				wakeupListener);
		this.stateChangeScanner = new StateChangeScanner(this);

		// Latency of 10 seconds for the scan.
		new Timer(true).schedule(stateChangeScanner, 0, 5 * 1000);

		LOG.info("start() <--");

	}

	public MudCacheDataStoreListener getMudCacheDatastoreListener() {
		return this.mudCacheDatastoreListener;
	}

	public short getSrcDeviceManufacturerStampTable() {
		return this.sdnmudConfig.getTableStart().shortValue();
	}

	public short getDstDeviceManufacturerStampTable() {
		return (short) (this.sdnmudConfig.getTableStart().shortValue() + 1);
	}

	public short getSrcMatchTable() {
		return (short) (this.sdnmudConfig.getTableStart() + 2);
	}

	public short getDstMatchTable() {
		return (short) (this.sdnmudConfig.getTableStart() + 3);
	}

	public short getNormalRulesTable() {
		return (short) (this.sdnmudConfig.getBroadcastRuleTable().shortValue());
	}

	public short getDropTable() {
		if (sdnmudConfig.getDropRuleTable() != null) {
			return (this.sdnmudConfig.getDropRuleTable().shortValue());
		} else {
			return (short) (getNormalRulesTable() + 1);
		}
	}

	/**
	 * Method called when the blueprint container is destroyed.
	 */
	public void close() {
		LOG.info("SdnmudProvider Closed");
		this.dataTreeChangeListenerRegistration.close();
		this.aclRegistration.close();
		this.configRegistration.close();
		this.mudCacheRegistration.close();
		this.mappingRegistration.close();
		this.packetInDispatcherRegistration.close();
		this.uriToMudMap.clear();
		this.packetInDispatcher.close();
		this.stateChangeScanner.cancel();
		this.sdnmudServiceRegistration.close();
		this.mudProfileRegistration.close();
		this.quaranteneDevicesListenerRegistration.close();
		if (mudReporter != null) {
			this.mudReporter.cancel();
		}
	}

	public StateChangeScanner getStateChangeScanner() {
		return this.stateChangeScanner;
	}

	public MudProfileDataStoreListener getMudProfileDataStoreListener() {
		return this.mudProfileDataStoreListener;
	}

	/**
	 * @return the mappingDataStoreListener
	 */
	public MappingDataStoreListener getMappingDataStoreListener() {
		return mappingDataStoreListener;
	}

	/**
	 * @return the aclDataStoreListener
	 */
	public AclDataStoreListener getAclDataStoreListener() {
		return aclDataStoreListener;
	}

	public QuaranteneDevicesListener getQuaranteneDevicesListener() {
		return this.quaranteneDevicesListener;
	}

	public ControllerclassMappingDataStoreListener getControllerclassMappingDataStoreListener() {
		return this.controllerClassMappingDataStoreListener;
	}

	public FlowWriter getFlowWritier() {
		return this.flowWriter;
	}

	public FlowCommitWrapper getFlowCommitWrapper() {
		return this.flowCommitWrapper;
	}

	public NotificationService getNotificationService() {
		return this.notificationService;
	}

	public DataBroker getDataBroker() {
		return this.dataBroker;
	}

	public DOMDataBroker getDomDataBroker() {
		return this.domDataBroker;
	}

	public BindingNormalizedNodeSerializer getBindingNormalizedNodeSerializer() {
		return this.bindingNormalizedNodeSerializer;
	}

	/**
	 * Put in the node to URI map.
	 *
	 * @param nodeUri  -- the node Uri.
	 * @param nodePath -- the flow capable node Instance Identifier.
	 */
	synchronized void putInUriToNodeMap(String nodeUri, InstanceIdentifier<FlowCapableNode> nodePath) {
		this.uriToNodeMap.put(nodeUri, nodePath);
		this.configStateChanged++;
	}

	/**
	 * Get the flow capable node id from the node uri.
	 *
	 * @param nodeUri -- the node URI
	 * @return -- the flow capable node.
	 */
	synchronized InstanceIdentifier<FlowCapableNode> getNode(String nodeUri) {
		return uriToNodeMap.get(nodeUri);
	}

	synchronized Collection<InstanceIdentifier<FlowCapableNode>> getNodes() {
		return uriToNodeMap.values();
	}

	synchronized void removeNode(String nodeUri) {
		InstanceIdentifier<FlowCapableNode> node = this.uriToNodeMap.remove(nodeUri);
		if (node == null) {
			LOG.info("remvoeNode: Cannot find node to remove");
			return;
		}

		for (Iterator<MacAddress> it = macToNodeIdMap.keySet().iterator(); it.hasNext();) {
			MacAddress ma = it.next();
			HashSet<String> hs = this.macToNodeIdMap.get(ma);
			if (hs != null && hs.contains(nodeUri)) {
				hs.remove(nodeUri);
				if (hs.isEmpty()) {
					it.remove();
				}
			}
		}

		// clean up the mudNodesMap
		for (Iterator<String> mudNodesIterator = this.mudNodesMap.keySet().iterator(); mudNodesIterator.hasNext();) {
			String manufacturer = mudNodesIterator.next();
			this.mudNodesMap.get(manufacturer).remove(node);
			if (mudNodesMap.get(manufacturer).isEmpty()) {
				mudNodesIterator.remove();
			}
		}

	}

	/**
	 * Add a MUD node for this device MAC address.
	 *
	 * @param deviceMacAddress -- mac address of device.
	 *
	 * @param node             -- the node to add.
	 */
	public void addMudNode(String manufacturerId, InstanceIdentifier<FlowCapableNode> node) {

		HashSet<InstanceIdentifier<FlowCapableNode>> nodes = this.mudNodesMap.get(manufacturerId);
		if (nodes == null) {
			nodes = new HashSet<InstanceIdentifier<FlowCapableNode>>();
			this.mudNodesMap.put(manufacturerId, nodes);
		}
		nodes.add(node);
	}

	/**
	 * Get the MUD nodes where flow rules were installed.
	 *
	 * @param deviceMacAddress -- the mac address for which we want the flow capable
	 *                         node set.
	 */
	public Collection<InstanceIdentifier<FlowCapableNode>> getMudNodes(String manufacturer) {
		return this.mudNodesMap.get(manufacturer);
	}

	public MudFlowsInstaller getMudFlowsInstaller() {
		return this.mudFlowsInstaller;
	}

	public DatastoreUpdater getDatastoreUpdater() {
		return this.datastoreUpdater;
	}

	public SalFlowService getFlowService() {
		return flowService;
	}

	public PacketProcessingService getPacketProcessingService() {
		return packetProcessingService;
	}

	public WakeupOnFlowCapableNode getWakeupListener() {
		return wakeupListener;
	}

	public void addMudUri(String cpeNodeId, Uri mudUri) {
		List<Uri> mudUris = this.nodeToMudUriMap.get(cpeNodeId);
		if (mudUris == null) {
			mudUris = new ArrayList<Uri>();
			this.nodeToMudUriMap.put(cpeNodeId, mudUris);
		}
		mudUris.add(mudUri);
	}

	public Collection<String> getMudCpeNodeIds() {
		return this.nodeToMudUriMap.keySet();
	}

	public List<Uri> getRegisteredMudUrls(String cpeNodeId) {
		return this.nodeToMudUriMap.get(cpeNodeId);
	}

	public Collection<String> getCpeSwitches() {
		return this.cpeNodes;
	}

	public Collection<Mud> getMudProfiles() {
		return this.uriToMudMap.values();
	}

	public synchronized void addMudProfile(Mud mud) {
		this.uriToMudMap.put(mud.getMudUrl().getValue(), mud);
		this.configStateChanged++;
	}

	public boolean hasMudProfile(String mudUrl) {
		return this.uriToMudMap.containsKey(mudUrl);
	}

	public boolean isCpeNode(String nodeId) {
		return this.cpeNodes.contains(nodeId);
	}

	/**
	 * @return
	 */
	public SchemaService getSchemaService() {
		return this.schemaService;
	}

	public OpendaylightDirectStatisticsService getDirectStatisticsService() {
		return this.directStatisticsService;
	}

	/**
	 * Add Aces for a given acl name scoped to a MUD URI.
	 *
	 * @param mudUri  -- the mudUri for wich to add the aces.
	 *
	 * @param aclName -- the acl name for which we want to add aces.
	 *
	 * @param aces    -- the ACE entries to add.
	 */
	public void addAces(String aclName, Aces aces) {
		LOG.info("adding ACEs aclName =   [" + aclName + "]");
		this.nameToAcesMap.put(aclName, aces);
		this.configStateChanged++;
	}

	/**
	 * Get the aces for a given acl name.
	 *
	 * @param aclName -- acl name
	 * @return -- Aces list for the acl name
	 */
	public Aces getAces(Uri mudUrl, String aclName) {
		LOG.info("getAces [" + mudUrl.getValue() + "/" + aclName + "]");
		return this.nameToAcesMap.get(mudUrl.getValue().hashCode() + "/" + aclName);
	}

	/**
	 * @param controllerMapping
	 */
	public void addControllerMap(ControllerclassMapping controllerMapping) {
		String nodeId = controllerMapping.getSwitchId().getValue();
		LOG.info("SdnmudProvider: Registering Controller for SwitchId " + nodeId);
		this.cpeNodes.add(nodeId);
		this.controllerClassMaps.put(nodeId, controllerMapping);
		HashMap<String, List<Ipv4Address>> map = this.controllerMap.get(nodeId);
		if (this.controllerMap.get(nodeId) == null) {
			map = new HashMap<>();
			this.controllerMap.put(nodeId, map);
		}

		for (Controller controller : controllerMapping.getController()) {
			String name = controller.getUri().getValue();
			List<Ipv4Address> addresses = controller.getAddressList();
			map.put(name, addresses);
		}

		this.configStateChanged++;

	}

	public void addControllerMap(String nodeId, String name, String addressStr) {
		HashMap<String, List<Ipv4Address>> map = this.controllerMap.get(nodeId);
		Ipv4Address address = new Ipv4Address(addressStr);

		if (this.controllerMap.get(nodeId) == null) {
			map = new HashMap<>();
			this.controllerMap.put(nodeId, map);
		}

		List<Ipv4Address> addresses = map.get(name);
		if (addresses == null) {
			addresses = new ArrayList<Ipv4Address>();
			map.put(name, addresses);
		}
		addresses.add(new Ipv4Address(address));

		this.configStateChanged++;
	}

	public String getControllerMappingForAddress(String nodeId, String addressStr) {
		Ipv4Address address = new Ipv4Address(addressStr);
		HashMap<String, List<Ipv4Address>> map = this.controllerMap.get(nodeId);
		for (String controller : map.keySet()) {
			List<Ipv4Address> addresses = map.get(controller);
			if (addresses.contains(address)) {
				return controller;
			}
		}
		return null;
	}

	/**
	 * @param nodeUri
	 * @return
	 */
	public Map<String, List<Ipv4Address>> getControllerClassMap(String nodeUri) {
		return controllerMap.get(nodeUri);
	}

	public Collection<String> getLocalNetworks(String nodeUri) {
		if (controllerClassMaps.get(nodeUri) == null)
			return null;
		return this.controllerClassMaps.get(nodeUri).getLocalNetworks();
	}

	public Collection<String> getLocalNetworksExclude(String nodeUri) {
		if (controllerClassMaps.get(nodeUri) == null)
			return null;
		return this.controllerClassMaps.get(nodeUri).getLocalNetworksExcludedHosts();
	}

	public boolean isControllerMapped() {
		return !this.controllerMap.isEmpty();
	}

	/**
	 * @return
	 */
	public boolean isConfigStateChanged() {
		return this.configStateChanged > 0;
	}

	public void clearConfigStateChanged() {
		this.configStateChanged--;
	}

	public void updateConfigStateChanged() {
		this.configStateChanged++;
	}

	/**
	 * @param sdnmudConfig
	 */
	public void setSdnmudConfig(SdnmudConfig sdnmudConfig) {
		this.sdnmudConfig = sdnmudConfig;
        // TODO -- check this. Why?
		this.mudReporter = new MudReportSender(this);
		if (sdnmudConfig.getReporterFrequency() != null) {
			new Timer(true).schedule(mudReporter, sdnmudConfig.getReporterFrequency() / 2 * 1000,
					sdnmudConfig.getReporterFrequency() / 2 * 1000);
		}
	}

	public SdnmudConfig getSdnmudConfig() {
		return this.sdnmudConfig;
	}

	public PacketInDispatcher getPacketInDispatcher() {
		return this.packetInDispatcher;
	}

	/**
	 * @return
	 */
	public FlowWriter getFlowWriter() {
		return this.flowWriter;
	}

	public void clearMudRules() {
		this.nodeToMudUriMap.clear();
		this.nameToAcesMap.clear();
		this.uriToMudMap.clear();
	}

	public MudFileFetcher getMudFileFetcher() {
		return mudFileFetcher;
	}

	public boolean isWirelessSwitch(String nodeUri) {
		return this.controllerClassMaps.get(nodeUri) != null && this.controllerClassMaps.get(nodeUri).isWireless();
	}

	public short getTableStart() {
		return this.sdnmudConfig.getTableStart().shortValue();
	}

	public NameResolutionCache getNameResolutionCache() {
		return this.nameResolutionCache;
	}

	public NotificationPublishService getNotificationPublishService() {
		return this.notificationPublishService;
	}

	public OpendaylightFlowStatisticsService getFlowStatisticsService() {
		return this.flowStatisticsService;
	}

	public int getMudReporterMinTimeout() {
		return this.sdnmudConfig.getMfgIdRuleCacheTimeout().intValue();
	}

	public Mud getMud(Uri mudUrl) {
		return this.uriToMudMap.get(mudUrl.getValue());
	}

	public DOMSchemaService getDomSchemaService() {
		return domSchemaService;
	}

	public HashMap<String, List<Ipv4Address>> getControllerMap(String nodeId) {
		return this.controllerMap.get(nodeId);
	}

	public boolean findMud(String mudUrl) {
		return this.uriToMudMap.containsKey(mudUrl);
	}
	
	public HashSet<String> findMuds() {
		HashSet<String> retval = new HashSet<String>();
		for (String i : uriToMudMap.keySet()) {
			retval.add(i);
		}
		return retval;
	}

}
