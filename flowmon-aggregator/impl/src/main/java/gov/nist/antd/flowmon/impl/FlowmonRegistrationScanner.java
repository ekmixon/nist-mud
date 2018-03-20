/*
 * Copyright © 2017 None.
 *
 * This program and the accompanying materials are in the public domain.
 * 
 * 
 */

package gov.nist.antd.flowmon.impl;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.TimerTask;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.flowmon.config.rev170915.FlowmonConfigData;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.Topology;
import org.opendaylight.yang.gen.v1.urn.nist.params.xml.ns.yang.nist.network.topology.rev170915.links.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scan for Flowmonitor registrations and install flow rules.
 * 
 * @author mranga
 *
 */
public class FlowmonRegistrationScanner extends TimerTask {

	static final Logger LOG = LoggerFactory.getLogger(FlowmonRegistrationScanner.class);

	private FlowmonProvider flowmonProvider;

	public FlowmonRegistrationScanner(FlowmonProvider flowmonProvider) {
		this.flowmonProvider = flowmonProvider;
	}

	private static String getManufacturer(Uri flowSpec) {

		if (flowSpec.getValue().equals(SdnMudConstants.PASSTHRU))
			return null;
		String[] pieces = flowSpec.getValue().split(":");
		return pieces[0];

	}

	private static String getFlowType(Uri flowSpec) {

		if (flowSpec.getValue().equals(SdnMudConstants.PASSTHRU))
			return SdnMudConstants.PASSTHRU;
		String[] pieces = flowSpec.getValue().split(":");
		return pieces[1];
	}

	private void installStripMplsTagAndGoToL2Switch(InstanceIdentifier<FlowCapableNode> nodePath, FlowId flowId,
			Short stripMplsRuleTable, int label) {

		FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(flowId.getValue());

		FlowBuilder flow = FlowUtils.createMplsMatchPopMplsLabelAndGoToTable(flowCookie, flowId, stripMplsRuleTable,
				label);

		this.flowmonProvider.getFlowCommitWrapper().writeFlow(flow, nodePath);

	}

	private static BigInteger createSrcModelMetadata(String mudUri) {
		int modelId = InstanceIdentifierUtils.getModelId(mudUri);
		return BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.SRC_MODEL_SHIFT);
	}

	private static BigInteger createDstModelMetadata(String mudUri) {
		return BigInteger.valueOf(InstanceIdentifierUtils.getModelId(mudUri))
				.shiftLeft(SdnMudConstants.DST_MODEL_SHIFT);
	}

	private static BigInteger createSrcManufacturerMetadata(String manufacturer) {
		return BigInteger.valueOf(InstanceIdentifierUtils.getManfuacturerId(manufacturer))
				.shiftLeft(SdnMudConstants.SRC_MANUFACTURER_SHIFT);
	}

	private static BigInteger createDstManufacturerModelMetadata(String mudUri) {
		int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(InstanceIdentifierUtils.getAuthority(mudUri));
		int modelId = InstanceIdentifierUtils.getModelId(mudUri);
		return BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT)
				.or(BigInteger.valueOf(modelId).shiftLeft(SdnMudConstants.DST_MODEL_SHIFT));

	}

	private static BigInteger createDstManufacturerMetadata(String mudUri) {
		int manufacturerId = InstanceIdentifierUtils.getManfuacturerId(InstanceIdentifierUtils.getAuthority(mudUri));
		return BigInteger.valueOf(manufacturerId).shiftLeft(SdnMudConstants.DST_MANUFACTURER_SHIFT);
	}

	@Override
	public void run() {
		for (FlowmonConfigData flowmonConfigData : flowmonProvider.getFlowmonConfigs()) {
			List<Uri> flowSpec = flowmonConfigData.getFlowSpec();
			for (Link link : this.flowmonProvider.getTopology().getLink()) {
				String flowmonNodeId = link.getVnfSwitch().getValue();
				InstanceIdentifier<FlowCapableNode> flowmonNode = this.flowmonProvider.getNode(flowmonNodeId);
				if (flowmonNode == null) {
					LOG.info("IDS node not found");
					return;
				}
				FlowCommitWrapper flowCommitWrapper = flowmonProvider.getFlowCommitWrapper();
				this.flowmonProvider.garbageCollectFlowmonRegistrationRecords();
				String flowmonPorts = flowmonProvider.getFlowmonOutputPort(flowmonNodeId);

				// No IDS ports found.

				if (flowmonPorts == null) {
					LOG.debug("No IDS registrations found for " + flowmonNodeId);
					for (Uri uri : flowSpec) {
						for (Uri cpeSwitch : flowmonProvider.getCpeNodeFlowmon()) {
							InstanceIdentifier<FlowCapableNode> node = flowmonProvider.getNode(cpeSwitch.getValue());
							flowCommitWrapper.deleteFlows(node, uri.getValue(), SdnMudConstants.PASS_THRU_TABLE, null);
							flowCommitWrapper.deleteFlows(flowmonNode, uri.getValue(),
									SdnMudConstants.SDNMUD_RULES_TABLE, null);
						}
					}
					return;
				}

				LOG.debug("flowmonRegistration: found for flowmonNodeId : " + flowmonNodeId);

				int duration = flowmonConfigData.getFilterDuration();

				LOG.debug("flowmonRegistration: duration = " + duration);

				for (Uri uri : flowSpec) {
					String flowType = getFlowType(uri);

					LOG.info("Install flow rules for flow type " + flowType);

					FlowCookie flowCookie = InstanceIdentifierUtils.createFlowCookie(uri.getValue());
					int mplsTag = InstanceIdentifierUtils.getFlowHash(uri.getValue());

					/* Divert flow in the VNF switch. */

					FlowId flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());
					flowCommitWrapper.deleteFlows(flowmonNode, uri.getValue(), SdnMudConstants.SDNMUD_RULES_TABLE,
							null);

					if (flowType.equals(SdnMudConstants.LOCAL)) {
						/*
						 * If the flowType is LOCAL then we do not route the
						 * packets further when then ge to the VNF switch.
						 */
						FlowBuilder flow = FlowUtils.createOnMplsTagMatchPopMplsTagsAndSendToPort(flowCookie, flowId,
								InstanceIdentifierUtils.getFlowHash(uri.getValue()), flowmonNodeId, flowmonPorts,
								SdnMudConstants.SDNMUD_RULES_TABLE, duration);

						// Install the flow.
						flowCommitWrapper.writeFlow(flow, flowmonNode);

					} else if (flowType.equals(SdnMudConstants.REMOTE)) {
						/*
						 * Flows that match some MUD rule that are sent to the
						 * IDS. The packets that do not any unclassified device
						 * are assumed to have been filtered through a MUD rule.
						 * Note that FlowChangeListener populates the device
						 * classification tables 
						 */
						BigInteger metadata = BigInteger
								.valueOf(InstanceIdentifierUtils.getFlowHash(SdnMudConstants.UNCONDITIONAL_GOTO));
						BigInteger metadataMask = new BigInteger("ffffffffffffffff", 16);
						FlowBuilder fb = FlowUtils.createMetadataMatchSendToPortsAndGotoTable(flowCookie, flowId,
								metadata, metadataMask, SdnMudConstants.SDNMUD_RULES_TABLE, flowmonPorts, duration);
						flowCommitWrapper.writeFlow(fb, flowmonNode);

					} else if (flowType.equals(SdnMudConstants.PASSTHRU)) {
						/*
						 * PASSTHRU flows are flows that do not have any MUD 
						 * rule asociated with them  that are sent from CPE
						 * via the VNF switch. This is the most common case
						 * that we would be interested in montoring.
						 * Install flows on the VNF switch. Get the packets
						 * that match the MPLS tag and send it to the IDS.
						 */
 						flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());
 						
 						BigInteger metadata = BigInteger
								.valueOf(InstanceIdentifierUtils.getFlowHash(SdnMudConstants.PASSTHRU));
						BigInteger metadataMask = new BigInteger("ffffffffffffffff", 16);
						FlowBuilder fb = FlowUtils.createMetadataMatchSendToPortsAndGotoTable(flowCookie, flowId,
								metadata, metadataMask, SdnMudConstants.SDNMUD_RULES_TABLE, flowmonPorts, duration);
						flowCommitWrapper.writeFlow(fb, flowmonNode);
					}

					/* get the cpe nodes corresponding to this VNF node */
					Collection<InstanceIdentifier<FlowCapableNode>> nodes = flowmonProvider.getCpeNodes(flowmonNodeId);

					if (nodes != null) {

						for (InstanceIdentifier<FlowCapableNode> node : nodes) {

							String sourceNodeUri = InstanceIdentifierUtils.getNodeUri(node);
							if (sourceNodeUri == null) {
								LOG.error("flowmonRegistration: Cannot find source node URI");
								continue;
							}
							if (flowmonProvider.isCpeNode(sourceNodeUri)) {

								flowmonProvider.getFlowCommitWrapper().deleteFlows(node, uri.getValue(),
										SdnMudConstants.PASS_THRU_TABLE, null);

								String outputPortUri = flowmonProvider.getFlowmonOutputPort(sourceNodeUri);
								if (outputPortUri == null) {
									LOG.info("Cannot find output port URI");
									continue;
								}
								LOG.debug("flowmonRegistration: outputPortUri " + outputPortUri);
								flowCommitWrapper.deleteFlows(node, uri.getValue(), SdnMudConstants.PASS_THRU_TABLE,
										null);
								if (flowType.equals(SdnMudConstants.LOCAL)) {
									/*
									 * This is the case when the IDS is interested in seeing local device to 
									 * device flows that are passed by MUD. The IDS is intertested 
									 * in monitoring local communications between devices.
									 */
									flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());
									/*
									 * Match on metadata, set the MPLS tag and
									 * send to NPE switch and go to l2 switch.
									 */
									FlowBuilder fb = FlowUtils.createMetadataMatchSetMplsTagSendToPortAndGoToTable(
											flowCookie, flowId, SdnMudConstants.PASS_THRU_TABLE, mplsTag, outputPortUri,
											duration);
									// Write the flow to the data store.
									flowCommitWrapper.writeFlow(fb, node);
									// Install a flow to strip the mpls label.
									flowId = InstanceIdentifierUtils.createFlowId(uri.getValue());
									// Strip MPLS tag and go to the L2 switch.
									installStripMplsTagAndGoToL2Switch(node, flowId,
											SdnMudConstants.STRIP_MPLS_RULE_TABLE, mplsTag);
								} 
							}
						}
					}

				}
			}

		}

	}

}
