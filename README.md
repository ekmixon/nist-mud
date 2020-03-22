##  Manufacturer Usage Description ##

IOT devices (AKA "Things") are special purpose devices that implement a dedicated function.
Such devices have communication patterns that are known a-priori to the manufacturer.

The goal of the MUD (Manufacturer Usage Description) specification is
to provide a means for manufacturers of Things to indicate what sort of
access and network functionality they require for the Thing to properly
function.  A manufacturer associates a MUD file with a device which
specifies an ACL for the device to within deployment specific parameters.

[The MUD standard is defined in RFC 8520](https://tools.ietf.org/html/rfc8520)

This repository publishes a public domain scalable implementation of
the  IETF MUD standard.  MUD is implemented on SDN capable switches
using OpenDaylight as the SDN controller.

This project is part of the [Trustworthy Networks of Things](https://www.nist.gov/programs-projects/trustworthy-networks-things) project at NIST.


## Implementation Highlights ##

* SDN-MUD : implements MUD ACLs on SDN Switches. 
  Implements the full set of MUD-defined ACLs (including Manufacturer, Controller, Model classes).
  *Does NOT Implement general Network ACLs*
* Model Driven design : Works directly with the IETF published YANG models.
* Implements DHCP or Directly administered MUD profiles. DHCP support is transparent - does not depend on modifications to the
  DHCP server. DHCP interactions are handled in the SDN controller.
* Scalable - *O(N)* flow rules for *N* distinct MAC addresses at a switch.
* Implements dynamic DNS

## Read a short paper about it ##


[A paper that describes this implementation](https://github.com/usnistgov/nist-mud/blob/master/docs/arch/icn2019-r7.pdf)

[A detailed explanation of the design with some examples. This corrects an error the paper above. ](https://www.nccoe.nist.gov/publication/1800-15/VolB/index.html#build-4)

## OpendDaylight Components ##

OpenDaylight is used as the SDN controller. 

*features-sdnmud* is the scalable MUD implementation.  This application manages the mud-specific flow rules on the CPE switches.



## Building ##

On the Controller host:

* Install JDK 1.8x. (There are some compile issues with higher versions)
* Install maven 3.5 or higher.
* Eclipse -- highly recommended if you want to look at code.

Copy maven/settings.xml to $HOME/.m2

Run maven

       mvn -e clean install -nsu -Dcheckstyle.skip -DskipTests -Dmaven.javadoc.skip=true

This will download the necessary dependencies and build the subprojects. (Note that we have disabled 
unit tests and javadoc creation with these flags. We are still in Alpha state here.)

Once the build is complete:

     cd sdnmud-aggregator/impl/karaf/target/assembly/bin
     ./karaf clean

At the karaf prompt 

     karaf> feature:install features-sdnmud

Will install the sdn mud feature (see below for configuration details).
Open port 6653 on your controller stack for tcp access so your switches
can connect.

Opendaylight uses port 8181 for REST API so you'll want to make sure
nothing is using that port and open up the port using UFW if you want
to access the REST api from outside the controller machine.

Check if the feature is running by doing the following:

      karaf> feature:list | grep sdnmud
      features-sdnmud                                 | 0.1.0            | x        | Started     | features-sdnmud                                 | ODL :: gov.nist.antd :: features-sdnmud
      odl-sdnmud-api                                  | 0.1.0            |          | Started     | odl-sdnmud-api                                  | OpenDaylight :: sdnmud :: API [Karaf Feature]
      odl-sdnmud                                      | 0.1.0            |          | Started     | odl-sdnmud-0.1.0                                | OpenDaylight :: sdnmud :: Impl [Karaf Feature]

Note: Your switches should support OpenFlow 1.5. Detailed configuration information is presented next.

### SYSTEM CONFIGURATION DETAIL ###

[Here is a detailed build and configuration guide with examples](https://www.nccoe.nist.gov/publication/1800-15/VolC/index.html#build-4-product-installation-guides)


[Also see the instructions in the doc/config directory](docs/config/README.md)


## Try it out  ##

The following is common configuration for Demo and Test. The following describes
how to exercise the MUD feature.


### DEMO ###

[See the instructions in the test/system-test directory](test/system-test/README.md)


### Tests ###

[See the instructions in the test/unittest directory](test/unittest/README.md)

## CONTRIBUTING ##

Contributions are eagerly solicited. In order to contribute to this project, please git fork the repository and
make your additions there. Then please post an issue with a pointer to a pull request that targets the MASTER branch.

See here on how to create a pull request from a fork:

https://help.github.com/articles/creating-a-pull-request-from-a-fork/

Your contributions will be acknowledged.

## LIMITATIONS and CAVEATS ##

**This is ALPHA code.** 

It works but has only been lightly tested. Much more testing and validation is needed.
*This is experimental code.* Much more testing is needed before it can be
deployed in anything close to a  production network. The authors solicit
your help in testing and validation.

Here are specific limitation:

* This is an IPV4 only implementation of MUD. 
* X.509 extensions for MUD (i.e. 802.1AR device Identity) not implemented.
* LLDP extensions for MUD support are not implemented.
* This is not a general ACL implementation. Only MUD specific ACLs are implemented..
* Dynamic name resolution by Things  works but is not yet fully tested.



## Useful Links ##


[Mudmaker : Make your mud files here! https://www.mudmaker.org] (https://www.mudmaker.org)

[How to set up openVswitch on an openwrt router https://github.com/elmiomar/OmniaTurrisSetup](https://github.com/elmiomar/OmniaTurrisSetup)

[Eliot Lear's Blog https://www.ofcourseimright.com/](https://www.ofcourseimright.com/)

[MUD discuss mailing list mud-discuss@googlegroups.com](mailto:mud-discuss@googlegroups.com)


## Credits and Acknowledgements ##

* The MUD Standard was primarily authored by Eliot Lear (Cisco) in the IETF OPSAWG working group.
* SDN MUD design and implementation : M. Ranganathan <mranga@nist.gov>
* Testing : Omar Ilias Elmimouni <omarilias.elmimouni@nist.gov>
* Implementation Design Contributors : Charif Mahmoudi, Doug Montgomery
* Project Manager Doug Montgomery <dougm@nist.gov>
* This is a product of the Advanced Networking Technologies Division of the National Institute of Standards and Technology (NIST).
* This work was funded using a Bridge to The Future (BTF) grant at NIST.
* Code from the following projects has been included in this code base:
    *DNSJava  (DNS Message parsing)
    *Android project  (DHCP support)

## Copyrights and Disclaimers ##

The following disclaimer applies to all code that was written by employees
of the National Institute of Standards and Technology.

This software was developed by employees of the National Institute of
Standards and Technology (NIST), an agency of the Federal Government
and is being made available as a public service. Pursuant to title 17
United States Code Section 105, works of NIST employees are not subject
to copyright protection in the United States.  This software may be
subject to foreign copyright.  Permission in the United States and in
foreign countries, to the extent that NIST may hold copyright, to use,
copy, modify, create derivative works, and distribute this software
and its documentation without fee is hereby granted on a non-exclusive
basis, provided that this notice and disclaimer of warranty appears in
all copies.

THE SOFTWARE IS PROVIDED 'AS IS' WITHOUT ANY WARRANTY OF ANY KIND,
EITHER EXPRESSED, IMPLIED, OR STATUTORY, INCLUDING, BUT NOT LIMITED
TO, ANY WARRANTY THAT THE SOFTWARE WILL CONFORM TO SPECIFICATIONS, ANY
IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
AND FREEDOM FROM INFRINGEMENT, AND ANY WARRANTY THAT THE DOCUMENTATION
WILL CONFORM TO THE SOFTWARE, OR ANY WARRANTY THAT THE SOFTWARE WILL
BE ERROR FREE.  IN NO EVENT SHALL NIST BE LIABLE FOR ANY DAMAGES,
INCLUDING, BUT NOT LIMITED TO, DIRECT, INDIRECT, SPECIAL OR CONSEQUENTIAL
DAMAGES, ARISING OUT OF, RESULTING FROM, OR IN ANY WAY CONNECTED WITH
THIS SOFTWARE, WHETHER OR NOT BASED UPON WARRANTY, CONTRACT, TORT, OR
OTHERWISE, WHETHER OR NOT INJURY WAS SUSTAINED BY PERSONS OR PROPERTY
OR OTHERWISE, AND WHETHER OR NOT LOSS WAS SUSTAINED FROM, OR AROSE OUT
OF THE RESULTS OF, OR USE OF, THE SOFTWARE OR SERVICES PROVIDED HEREUNDER.

[See official statements here](https://www.nist.gov/director/copyright-fair-use-and-licensing-statements-srd-data-and-software)


Specific copyrights for code that has been re-used from other open 
source projects are noted in the source files as appropriate.
Please acknowledge our work if you re-use this code or design.

![alt tag](docs/logos/nist-logo.png)
