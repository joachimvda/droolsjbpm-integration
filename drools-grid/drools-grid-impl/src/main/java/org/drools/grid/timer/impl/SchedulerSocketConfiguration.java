package org.drools.grid.timer.impl;

import org.drools.grid.conf.GridPeerServiceConfiguration;
import org.drools.grid.service.directory.impl.*;
import java.net.InetSocketAddress;
import org.drools.grid.CoreServicesLookup;

import org.drools.grid.Grid;
import org.drools.grid.GridServiceDescription;
import org.drools.grid.MessageReceiverHandlerFactoryService;
import org.drools.grid.SocketService;
import org.drools.grid.service.directory.Address;
import org.drools.time.SchedulerService;

public class SchedulerSocketConfiguration
    implements
    GridPeerServiceConfiguration {
    private int port = -1;

    public SchedulerSocketConfiguration(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public void configureService(Grid grid) {
        SchedulerService sched = grid.get( SchedulerService.class );

        if ( port != -1 ) {
            CoreServicesLookupImpl coreServicesWP = (CoreServicesLookupImpl) grid.get( CoreServicesLookup.class );

            GridServiceDescriptionImpl gsd = (GridServiceDescriptionImpl) coreServicesWP.lookup( SchedulerService.class );
            if ( gsd == null ) {
                gsd = new GridServiceDescriptionImpl( SchedulerService.class, grid.getId() );
            }

            SocketService mss = grid.get( SocketService.class );

            //            GridServiceDescription service = coreServicesWP.getServices().get( SchedulerService.class.getName() );
            //            if( service == null){
            //                coreServicesWP.getServices().put(SchedulerService.class.getName(), gsd);
            //                service = gsd;
            //            }
            //            Address address = null;
            //            if(service.getAddresses().get("socket") != null){
            //                address = service.getAddresses().get("socket");
            //            } else{
            //                address = service.addAddress( "socket" );
            //            }
            //            InetSocketAddress[] addresses = (InetSocketAddress[])address.getObject();
            //            if(addresses != null && addresses.length >= 1){
            //                 InetSocketAddress[] newAddresses = new InetSocketAddress[addresses.length+1];
            //                if(addresses !=null){
            //                    System.arraycopy(addresses, 0, newAddresses, 0, addresses.length);
            //                }
            //                newAddresses[addresses.length]= new InetSocketAddress( mss.getIp(),
            //                                                             this.port);
            //                 ServiceConfiguration conf = new SchedulerServiceConfiguration(newAddresses);
            //                 service.setData(conf);
            //            }else{
            //                 InetSocketAddress[] newAddress = new InetSocketAddress[1];
            //                 newAddress[0]= new InetSocketAddress( mss.getIp(),
            //                                                         this.port);
            //                 address.setObject(  newAddress );
            //                 ServiceConfiguration conf = new SchedulerServiceConfiguration(newAddress);
            //                 service.setData(conf);
            //            }

            mss.addService( SchedulerService.class.getName(),
                            this.port,
                            sched );
        }
    }
}
