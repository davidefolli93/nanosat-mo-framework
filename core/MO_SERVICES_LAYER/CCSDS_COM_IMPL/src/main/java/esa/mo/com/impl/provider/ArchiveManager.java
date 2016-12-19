/* ----------------------------------------------------------------------------
 * Copyright (C) 2015      European Space Agency
 *                         European Space Operations Centre
 *                         Darmstadt
 *                         Germany
 * ----------------------------------------------------------------------------
 * System                : ESA NanoSat MO Framework
 * ----------------------------------------------------------------------------
 * Licensed under the European Space Agency Public License, Version 2.0
 * You may not use this file except in compliance with the License.
 *
 * Except as expressly set forth in this License, the Software is provided to
 * You on an "as is" basis and without warranties of any kind, including without
 * limitation merchantability, fitness for a particular purpose, absence of
 * defects or errors, accuracy or non-infringement of intellectual property rights.
 * 
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 * ----------------------------------------------------------------------------
 */
package esa.mo.com.impl.provider;

import esa.mo.com.impl.archive.db.COMObjectPK;
import esa.mo.com.impl.archive.db.FastObjId;
import esa.mo.com.impl.archive.db.FastDomain;
import esa.mo.com.impl.util.HelperCOM;
import esa.mo.helpertools.connections.ConfigurationProvider;
import esa.mo.helpertools.helpers.HelperAttributes;
import esa.mo.helpertools.helpers.HelperMisc;
import esa.mo.com.impl.archive.db.DatabaseBackend;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.Query;
import org.ccsds.moims.mo.com.archive.ArchiveHelper;
import org.ccsds.moims.mo.com.archive.structures.ArchiveDetails;
import org.ccsds.moims.mo.com.archive.structures.ArchiveDetailsList;
import org.ccsds.moims.mo.com.archive.structures.ArchiveQuery;
import org.ccsds.moims.mo.com.archive.structures.CompositeFilter;
import org.ccsds.moims.mo.com.archive.structures.CompositeFilterList;
import org.ccsds.moims.mo.com.archive.structures.CompositeFilterSet;
import org.ccsds.moims.mo.com.archive.structures.ExpressionOperator;
import org.ccsds.moims.mo.com.structures.ObjectId;
import org.ccsds.moims.mo.com.structures.ObjectIdList;
import org.ccsds.moims.mo.com.structures.ObjectKey;
import org.ccsds.moims.mo.com.structures.ObjectType;
import org.ccsds.moims.mo.mal.MALContextFactory;
import org.ccsds.moims.mo.mal.MALException;
import org.ccsds.moims.mo.mal.MALInteractionException;
import org.ccsds.moims.mo.mal.provider.MALInteraction;
import org.ccsds.moims.mo.mal.structures.Attribute;
import org.ccsds.moims.mo.mal.structures.Blob;
import org.ccsds.moims.mo.mal.structures.Composite;
import org.ccsds.moims.mo.mal.structures.Element;
import org.ccsds.moims.mo.mal.structures.ElementList;
import org.ccsds.moims.mo.mal.structures.Enumeration;
import org.ccsds.moims.mo.mal.structures.IdentifierList;
import org.ccsds.moims.mo.mal.structures.LongList;
import org.ccsds.moims.mo.mal.structures.UInteger;
import org.ccsds.moims.mo.mal.structures.UIntegerList;
import org.ccsds.moims.mo.mal.structures.URI;

/**
 *
 * @author Cesar Coelho
 */
public class ArchiveManager {

    private final FastObjId fastObjId;
//    private final FastDomain fastDomain;
    private final DatabaseBackend dbBackend;
    private static final Boolean SAFE_MODE = true;

    private EventProviderServiceImpl eventService;
    private final ConfigurationProvider configuration = new ConfigurationProvider();

//    private final LinkedBlockingQueue<ArrayList<ArchivePersistenceObject>> storeQueue;
    private Thread storingThread;

    /**
     * Initializes the Archive manager
     *
     * @param eventService
     */
    public ArchiveManager(EventProviderServiceImpl eventService) {
        this.eventService = eventService;

        try {
            ArchiveHelper.init(MALContextFactory.getElementFactoryRegistry());
        } catch (MALException ex) {
        }

//        this.storeQueue = new LinkedBlockingQueue<ArrayList<ArchivePersistenceObject>>();

        this.dbBackend = new DatabaseBackend();
        this.dbBackend.startBackendDatabase();

        // Start the separate lists for the "fast" generation of objIds
        this.fastObjId = new FastObjId(dbBackend);
//        this.fastDomain = new FastDomain(dbBackend);

        /*        
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            Logger.getLogger(ArchiveManager.class.getName()).log(Level.SEVERE, null, ex);
        }
         */
    }

    void init() {
/*
        storingThread = new Thread() {
            @Override
            public void run() {
                this.setName("ArchiveManager_StoringThread");
                ArrayList<ArchivePersistenceObject> perObjs;
                ArrayList<ArchivePersistenceObject> all;

                while (true) {
                    all = new ArrayList<ArchivePersistenceObject>();
                    
                    try {
                        perObjs = storeQueue.take();
                        all.addAll(perObjs);

                        dbBackend.createEntityManager();  // 0.166 ms

                        for (int i = 0; i < 2; i++) {  // Give it two tries (will lock the db for a limited time)

//                long startTime = System.currentTimeMillis();
                            dbBackend.getEM().getTransaction().begin(); // 0.480 ms

//                Logger.getLogger(ArchiveManager.class.getName()).log(Level.INFO, "Time 1: " + (System.currentTimeMillis() - startTime));
                            while (perObjs != null) {
                                persistObjects(perObjs); // store

                                perObjs = storeQueue.poll(); // get next if there is one available

                                if (perObjs != null) {
                                    all.addAll(perObjs);  // Make it ready to be persisted
                                }
                            }
                            
//                Logger.getLogger(ArchiveManager.class.getName()).log(Level.INFO, "Time 2: " + (System.currentTimeMillis() - startTime));
                            dbBackend.safeCommit();

                            if(i == 0){  // Only works for the first iteration of the for loop
                                perObjs = storeQueue.poll(); // get next if there is one available

                                if (perObjs != null) {
                                    all.addAll(perObjs);  // Make it ready to be persisted
                                }
                                
                            }

                        }

//                Logger.getLogger(ArchiveManager.class.getName()).log(Level.INFO, "Time 3: " + (System.currentTimeMillis() - startTime));
                        dbBackend.closeEntityManager(); // 0.410 ms
//                Logger.getLogger(ArchiveManager.class.getName()).log(Level.INFO, "Time 4: " + (System.currentTimeMillis() - startTime));

                        // Generate and Publish the Events - requirement: 3.4.2.1
//                        generateAndPublishEvents(ArchiveHelper.OBJECTSTORED_OBJECT_TYPE, all, interaction);
                        generateAndPublishEvents(ArchiveHelper.OBJECTSTORED_OBJECT_TYPE, all, null);
//                Logger.getLogger(ArchiveManager.class.getName()).log(Level.INFO, "Time 5: " + (System.currentTimeMillis() - startTime));

                    } catch (InterruptedException e) {
                        Logger.getLogger(ArchiveManager.class.getName()).log(Level.SEVERE, null, e);
                    }
                }
            }
        };

        storingThread.start();
*/
    }

    protected void setEventService(EventProviderServiceImpl eventService) {
        this.eventService = eventService;
    }

    protected void resetTable() {

        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            Logger.getLogger(ArchiveManager.class.getName()).log(Level.SEVERE, null, ex);
        }

        dbBackend.createEntityManager();

        dbBackend.getEM().getTransaction().begin();
        dbBackend.getEM().createQuery("DELETE FROM ArchivePersistenceObject").executeUpdate();
        dbBackend.getEM().getTransaction().commit();
        dbBackend.getEM().close();

        this.fastObjId.resetFastIDs();
//        this.fastDomain.resetFastDomain();

        dbBackend.restartEMF();

        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            Logger.getLogger(ArchiveManager.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    protected ArchivePersistenceObject getPersistenceObject(final ObjectType objType, final IdentifierList domain, final Long objId) {
        dbBackend.createEntityManager();
        final COMObjectPK id = ArchivePersistenceObject.generatePK(objType, domain, objId);
        final ArchivePersistenceObject perObj = dbBackend.getEM().find(ArchivePersistenceObject.class, id);
        dbBackend.closeEntityManager();

        return perObj;
    }

    protected Object getObject(final ObjectType objType, final IdentifierList domain, final Long objId) {
        return this.getPersistenceObject(objType, domain, objId).getObject();
    }

    protected ArchiveDetails getArchiveDetails(final ObjectType objType, final IdentifierList domain, final Long objId) {
        return this.getPersistenceObject(objType, domain, objId).getArchiveDetails();
    }

    protected Boolean objIdExists(final ObjectType objType, final IdentifierList domain, final Long objId) {
        return (this.getPersistenceObject(objType, domain, objId) != null);
    }

    protected LongList getAllObjIds(final ObjectType objectType, final IdentifierList domain) {
        dbBackend.createEntityManager();
        Query query = dbBackend.getEM().createQuery("SELECT PU.objId FROM ArchivePersistenceObject PU WHERE PU.objectTypeId=:objectTypeId AND PU.domainId=:domainId");
        query.setParameter("objectTypeId", HelperCOM.generateSubKey(objectType));
        query.setParameter("domainId", HelperMisc.domain2domainId(domain));
        LongList objIds = new LongList();
        objIds.addAll(query.getResultList());
        dbBackend.closeEntityManager();

        return objIds;
    }

    protected LongList insertEntries(final ObjectType objType, final IdentifierList domain,
            ArchiveDetailsList lArchiveDetails, final ElementList objects, final MALInteraction interaction) {

        final LongList outIds = new LongList();
        final ArrayList<ArchivePersistenceObject> perObjs = new ArrayList<ArchivePersistenceObject>();

        // Generate the object Ids if needed and the persistence objects to be stored
        for (int i = 0; i < lArchiveDetails.size(); i++) {
            Long objId = this.fastObjId.getUniqueObjId(objType, domain, lArchiveDetails.get(i).getInstId());

            // If there are no objects in the list, inject null...
            Object objBody = (objects == null) ? null : ((objects.get(i) == null) ? null : objects.get(i));

            final ArchivePersistenceObject perObj = new ArchivePersistenceObject(objType,
                    domain, objId, lArchiveDetails.get(i), objBody); // 0.170 ms

            perObjs.add(perObj);
            outIds.add(objId);
        }

        /*
        try { // Insert into queue
            storeQueue.put(perObjs);
        } catch (InterruptedException ex) {
            Logger.getLogger(ArchiveManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        */

        
        dbBackend.createEntityManager();  // 0.166 ms

        Thread t1 = new Thread() {
            @Override
            public void run() {
//                long startTime = System.currentTimeMillis();
                
                dbBackend.getEM().getTransaction().begin(); // 0.480 ms

//                Logger.getLogger(ArchiveManager.class.getName()).log(Level.INFO, "Time 1: " + (System.currentTimeMillis() - startTime));
                persistObjects(perObjs);
//                Logger.getLogger(ArchiveManager.class.getName()).log(Level.INFO, "Time 2: " + (System.currentTimeMillis() - startTime));

                try {  // This is where the db takes longer!!
                    dbBackend.getEM().getTransaction().commit(); // 1.220 ms
                } catch (Exception ex) {
                    Logger.getLogger(ArchiveManager.class.getName()).log(Level.WARNING, "The object could not be commited! Waiting 500 ms and trying again...");
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex1) {
                        Logger.getLogger(ArchiveManager.class.getName()).log(Level.SEVERE, null, ex1);
                    }

                    try{
                        dbBackend.getEM().getTransaction().commit(); // 1.220 ms
                    } catch (Exception ex2) {
                        Logger.getLogger(ArchiveManager.class.getName()).log(Level.SEVERE, "The objects could not be commited on the second try!", ex2);
                    }
                }

//                Logger.getLogger(ArchiveManager.class.getName()).log(Level.INFO, "Time 3: " + (System.currentTimeMillis() - startTime));
                dbBackend.closeEntityManager(); // 0.410 ms
//                Logger.getLogger(ArchiveManager.class.getName()).log(Level.INFO, "Time 4: " + (System.currentTimeMillis() - startTime));

                // Generate and Publish the Events - requirement: 3.4.2.1
                generateAndPublishEvents(ArchiveHelper.OBJECTSTORED_OBJECT_TYPE, perObjs, interaction);
//                Logger.getLogger(ArchiveManager.class.getName()).log(Level.INFO, "Time 5: " + (System.currentTimeMillis() - startTime));
            }
        };

        t1.start(); // Total time thread: ~1.485 ms

        return outIds;
    }

    private void persistObjects(final ArrayList<ArchivePersistenceObject> perObjs) {
        for (int i = 0; i < perObjs.size(); i++) { // 6.510 ms per cycle
            if (SAFE_MODE) {
                final ArchivePersistenceObject objCOM = dbBackend.getEM().find(ArchivePersistenceObject.class, perObjs.get(i).getPrimaryKey()); // 0.830 ms

                if (objCOM == null) { // Last minute safety, the db crashes if one tries to store an object with a used pk
                    final ArchivePersistenceObject perObj = perObjs.get(i); // The object to be stored  // 0.255 ms
                    dbBackend.getEM().persist(perObj);  // object    // 0.240 ms
                } else {
                    Logger.getLogger(ArchiveManager.class.getName()).log(Level.SEVERE, "The Archive could not store the object: " + perObjs.get(i).toString());
                }
            } else {
                dbBackend.getEM().persist(perObjs.get(i));  // object
            }

            // Flush every 1k objects...
            if (i != 0) {
                if ((i % 1000) == 0) {
                    Logger.getLogger(ArchiveManager.class.getName()).log(Level.FINE, "Flushing the data after 1000 serial stores...");
                    dbBackend.getEM().flush();
                    dbBackend.getEM().clear();
                }
            }
        }
    }

    protected void updateEntries(final ObjectType objType, final IdentifierList domain,
            final ArchiveDetailsList lArchiveDetails, final ElementList objects, final MALInteraction interaction) {

        dbBackend.createEntityManager();  // 0.166 ms

        Thread t1 = new Thread() {
            @Override
            public void run() {
                final ArrayList<ArchivePersistenceObject> newObjs = new ArrayList<ArchivePersistenceObject>();

                // Generate the object Ids if needed and the persistence objects to be stored
                for (int i = 0; i < lArchiveDetails.size(); i++) {
                    // If there are no objects in the list, inject null...
                    Object objBody = (objects == null) ? null : ((objects.get(i) == null) ? null : objects.get(i));

                    final ArchivePersistenceObject newObj = new ArchivePersistenceObject(objType,
                            domain, lArchiveDetails.get(i).getInstId(), lArchiveDetails.get(i), objBody); // 0.170 ms

                    newObjs.add(newObj);
                }

                for (int i = 0; i < newObjs.size(); i++) {
                    final COMObjectPK id = ArchivePersistenceObject.generatePK(objType, domain, newObjs.get(i).getArchiveDetails().getInstId());
                    ArchivePersistenceObject previousObj = dbBackend.getEM().find(ArchivePersistenceObject.class, id);

                    dbBackend.getEM().getTransaction().begin();
                    dbBackend.getEM().remove(previousObj);
                    dbBackend.getEM().getTransaction().commit();

                    // Maybe we can replace the 3 lines below with a persistObjects(newObjs) after the for loop
                    dbBackend.getEM().getTransaction().begin();
                    dbBackend.getEM().persist(newObjs.get(i));
                    dbBackend.getEM().getTransaction().commit();
                }

                dbBackend.closeEntityManager(); // 0.410 ms

                // Generate and Publish the Events - requirement: 3.4.2.1
                generateAndPublishEvents(ArchiveHelper.OBJECTUPDATED_OBJECT_TYPE, newObjs, interaction);
            }
        };

        t1.start();

    }

    protected LongList removeEntries(final ObjectType objType, final IdentifierList domain,
            final LongList objIds, final MALInteraction interaction) {

        final ArrayList<ArchivePersistenceObject> perObjs = new ArrayList<ArchivePersistenceObject>();

        dbBackend.createEntityManager();  // 0.166 ms

        Thread t1 = new Thread() {
            @Override
            public void run() {
                // Generate the object Ids if needed and the persistence objects to be stored
                for (int i = 0; i < objIds.size(); i++) {
                    final COMObjectPK id = ArchivePersistenceObject.generatePK(objType, domain, objIds.get(i));
                    ArchivePersistenceObject perObj = dbBackend.getEM().find(ArchivePersistenceObject.class, id);

                    perObjs.add(perObj);
                }

                for (int i = 0; i < objIds.size(); i++) {
                    final COMObjectPK id = ArchivePersistenceObject.generatePK(objType, domain, objIds.get(i));
                    ArchivePersistenceObject perObj = dbBackend.getEM().find(ArchivePersistenceObject.class, id);
                    dbBackend.getEM().getTransaction().begin();
                    dbBackend.getEM().remove(perObj);
                    dbBackend.getEM().getTransaction().commit();
                }

//                fastObjId.lock();
                fastObjId.delete(objType, domain);
//                fastObjId.unlock();

                dbBackend.closeEntityManager(); // 0.410 ms

                // Generate and Publish the Events - requirement: 3.4.2.1
                generateAndPublishEvents(ArchiveHelper.OBJECTDELETED_OBJECT_TYPE, perObjs, interaction);
            }
        };

        t1.start(); // Total time thread: ~1.485 ms

        return objIds;
    }

    protected ArrayList<ArchivePersistenceObject> query(final ObjectType objType, final ArchiveQuery archiveQuery) {

        final boolean domainContainsWildcard = HelperCOM.domainContainsWildcard(archiveQuery.getDomain());
        final boolean objectTypeContainsWildcard = ArchiveManager.objectTypeContainsWildcard(objType);
        final boolean relatedContainsWildcard = (archiveQuery.getRelated().equals((long) 0));
        final boolean startTimeContainsWildcard = (archiveQuery.getStartTime() == null);
        final boolean endTimeContainsWildcard = (archiveQuery.getEndTime() == null);
        final boolean providerURIContainsWildcard = (archiveQuery.getProvider() == null);
        final boolean networkContainsWildcard = (archiveQuery.getNetwork() == null);

        final boolean hasSomeDefinedFields = (!domainContainsWildcard
                || !objectTypeContainsWildcard
                || !relatedContainsWildcard
                || !startTimeContainsWildcard
                || !endTimeContainsWildcard
                || !providerURIContainsWildcard
                || !networkContainsWildcard);

        // Generate the query string
        String queryString = "SELECT PU FROM ArchivePersistenceObject PU ";
        queryString += (hasSomeDefinedFields) ? "WHERE " : "";

        queryString += (domainContainsWildcard) ? "" : "PU.domainId=:domainId AND ";
        queryString += (objectTypeContainsWildcard) ? "" : "PU.objectTypeId=:objectTypeId AND ";
        queryString += (relatedContainsWildcard) ? "" : "PU.relatedLink=:relatedLink AND ";
        queryString += (startTimeContainsWildcard) ? "" : "PU.timestampArchiveDetails>=:startTime AND ";
        queryString += (endTimeContainsWildcard) ? "" : "PU.timestampArchiveDetails<=:endTime AND ";
        queryString += (providerURIContainsWildcard) ? "" : "PU.providerURI=:providerURI AND ";
        queryString += (networkContainsWildcard) ? "" : "PU.network=:network AND ";

        if (hasSomeDefinedFields) { // 4 because we are removing the "AND " part
            queryString = queryString.substring(0, queryString.length() - 4);
        }

        ArrayList<ArchivePersistenceObject> perObjs = new ArrayList<ArchivePersistenceObject>();

        dbBackend.createEntityManager();
        Query query = dbBackend.getEM().createQuery(queryString); // Make the query

        if (!objectTypeContainsWildcard) {
            query.setParameter("objectTypeId", HelperCOM.generateSubKey(objType));
        }

        if (!domainContainsWildcard) {
            query.setParameter("domainId", HelperMisc.domain2domainId(archiveQuery.getDomain()));
        }

        if (!relatedContainsWildcard) {
            query.setParameter("relatedLink", archiveQuery.getRelated());
        }

        if (!startTimeContainsWildcard) {
            query.setParameter("startTime", archiveQuery.getStartTime().getValue());
        }

        if (!endTimeContainsWildcard) {
            query.setParameter("endTime", archiveQuery.getEndTime().getValue());
        }

        if (!providerURIContainsWildcard) {
            query.setParameter("providerURI", archiveQuery.getProvider().getValue());
        }

        if (!networkContainsWildcard) {
            query.setParameter("network", archiveQuery.getNetwork().getValue());
        }

        Thread tThread = new Thread(new Runnable() { // Display a message in case the query gets stuck...
            @Override
            public void run() {
                try {
                    this.wait(10 * 1000); // 10 seconds
                    Logger.getLogger(ArchiveManager.class.getName()).log(Level.INFO, "The query is taking longer than 10 seconds. The query might be too broad to be handled by the database.");
                } catch (InterruptedException ex) {
                } catch (IllegalMonitorStateException ex) {
                }
            }
        });

        tThread.start();

        // BUG: The code will get stuck on the line below if the database is too big (tested with Derby)
        List resultList = query.getResultList();
        tThread.interrupt();
        perObjs.addAll(resultList);
        dbBackend.closeEntityManager();

        // Add domain filtering by subpart
        if (archiveQuery.getDomain() != null) {
            if (domainContainsWildcard) {  // It does contain a wildcard
                ArrayList<ArchivePersistenceObject> tmpPerObjs = new ArrayList<ArchivePersistenceObject>();
                IdentifierList tmpDomain;

                for (ArchivePersistenceObject perObj : perObjs) {
                    tmpDomain = perObj.getDomain();
                    if (HelperCOM.domainMatchesWildcardDomain(tmpDomain, archiveQuery.getDomain())) {  // Does the domain matches the wildcard?
                        tmpPerObjs.add(perObj);
                    }
                }

                perObjs = tmpPerObjs;  // Assign new filtered list and discard old one
            }
        }

        // If objectType contains a wildcard then we have to filter them
        if (ArchiveManager.objectTypeContainsWildcard(objType)) {

            final long bitMask = ArchiveManager.objectType2Mask(objType);
            final long objTypeId = HelperCOM.generateSubKey(objType);

            ArrayList<ArchivePersistenceObject> tmpPerObjs = new ArrayList<ArchivePersistenceObject>();
            long tmpObjectTypeId;
            long objTypeANDed;
            for (ArchivePersistenceObject perObj : perObjs) {
                tmpObjectTypeId = perObj.getObjectTypeId();
                objTypeANDed = (tmpObjectTypeId & bitMask);
                if (objTypeANDed == objTypeId) { // Comparison
                    tmpPerObjs.add(perObj);
                }
            }
            perObjs = tmpPerObjs;  // Assign new filtered list and discard old one

        }

        // Source field
        if (archiveQuery.getSource() != null) {

            if (ArchiveManager.objectTypeContainsWildcard(archiveQuery.getSource().getType())
                    || HelperCOM.domainContainsWildcard(archiveQuery.getSource().getKey().getDomain())
                    || archiveQuery.getSource().getKey().getInstId() == 0) { // Any Wildcards?
                // objectType filtering   (in the source link)
                if (ArchiveManager.objectTypeContainsWildcard(archiveQuery.getSource().getType())) {

                    final long bitMask = ArchiveManager.objectType2Mask(archiveQuery.getSource().getType());
                    final long objTypeId = HelperCOM.generateSubKey(archiveQuery.getSource().getType());

                    ArrayList<ArchivePersistenceObject> tmpPerObjs = new ArrayList<ArchivePersistenceObject>();
                    long tmpObjectTypeId;
                    long objTypeANDed;
                    for (ArchivePersistenceObject perObj : perObjs) {
                        tmpObjectTypeId = HelperCOM.generateSubKey(perObj.getArchiveDetails().getDetails().getSource().getType());
                        objTypeANDed = (tmpObjectTypeId & bitMask);
                        if (objTypeANDed == objTypeId) { // Comparison
                            tmpPerObjs.add(perObj);
                        }
                    }
                    perObjs = tmpPerObjs;  // Assign new filtered list and discard old one

                } else {

                    final long objTypeId = HelperCOM.generateSubKey(archiveQuery.getSource().getType());

                    ArrayList<ArchivePersistenceObject> tmpPerObjs = new ArrayList<ArchivePersistenceObject>();
                    long tmpObjectTypeId;
                    for (ArchivePersistenceObject perObj : perObjs) {
                        tmpObjectTypeId = HelperCOM.generateSubKey(perObj.getArchiveDetails().getDetails().getSource().getType());
                        if (tmpObjectTypeId == objTypeId) { // Comparison
                            tmpPerObjs.add(perObj);
                        }
                    }
                    perObjs = tmpPerObjs;  // Assign new filtered list and discard old one

                }

                // Add domain filtering by subpart  (in the source link)
                if (HelperCOM.domainContainsWildcard(archiveQuery.getSource().getKey().getDomain())) {  // Does it contain a wildcard?
                    ArrayList<ArchivePersistenceObject> tmpPerObjs = new ArrayList<ArchivePersistenceObject>();
                    IdentifierList tmpDomain;

                    for (ArchivePersistenceObject perObj : perObjs) {
                        tmpDomain = perObj.getArchiveDetails().getDetails().getSource().getKey().getDomain();
                        if (HelperCOM.domainMatchesWildcardDomain(tmpDomain, archiveQuery.getSource().getKey().getDomain())) {  // Does the domain matches the wildcard?
                            tmpPerObjs.add(perObj);
                        }
                    }

                    perObjs = tmpPerObjs;  // Assign new filtered list and discard old one
                } else {
                    ArrayList<ArchivePersistenceObject> tmpPerObjs = new ArrayList<ArchivePersistenceObject>();
                    IdentifierList tmpDomain;

                    for (ArchivePersistenceObject perObj : perObjs) {
                        tmpDomain = perObj.getArchiveDetails().getDetails().getSource().getKey().getDomain();
                        if (tmpDomain.equals(archiveQuery.getSource().getKey().getDomain())) {  // Does the domain matches the wildcard?
                            tmpPerObjs.add(perObj);
                        }
                    }

                    perObjs = tmpPerObjs;  // Assign new filtered list and discard old one

                }

                // ObjId
                if (archiveQuery.getSource().getKey().getInstId() != 0) {
                    ArrayList<ArchivePersistenceObject> tmpPerObjs = new ArrayList<ArchivePersistenceObject>();
                    final long queRelated = archiveQuery.getSource().getKey().getInstId();
                    long tmpRelated;
                    for (ArchivePersistenceObject perObj : perObjs) {
                        tmpRelated = perObj.getArchiveDetails().getDetails().getSource().getKey().getInstId();
                        if (tmpRelated == queRelated) { // Comparison  // It can never be null, disregard warning
                            tmpPerObjs.add(perObj);
                        }
                    }
                    perObjs = tmpPerObjs;  // Assign new filtered list and discard old one
                }

            } else {
                // Direct match without wildcards
                ArrayList<ArchivePersistenceObject> tmpPerObjs = new ArrayList<ArchivePersistenceObject>();
                final ObjectId queSource = archiveQuery.getSource();
                ObjectId tmpSource;
                for (ArchivePersistenceObject perObj : perObjs) {
                    tmpSource = perObj.getArchiveDetails().getDetails().getSource();
                    if (tmpSource.equals(queSource)) {
                        tmpPerObjs.add(perObj);
                    }
                }
                perObjs = tmpPerObjs;  // Assign new filtered list and discard old one
            }

        }

        return perObjs;
    }

    protected static ArrayList<ArchivePersistenceObject> filterQuery(
            final ArrayList<ArchivePersistenceObject> perObjs, final CompositeFilterSet filterSet)
            throws MALInteractionException {

        if (filterSet == null) {
            return perObjs;
        }
        CompositeFilterList compositeFilterList = filterSet.getFilters();
        ArrayList<ArchivePersistenceObject> outPerObjs = perObjs;
        ArrayList<ArchivePersistenceObject> tmpPerObjs;
        Object obj;

        // Cycle the Filters
        for (CompositeFilter compositeFilter : compositeFilterList) {
            tmpPerObjs = new ArrayList<ArchivePersistenceObject>();

            if (compositeFilter == null) {
                continue;
            }

            // Cycle the objects
            for (ArchivePersistenceObject outPerObj : outPerObjs) {
                obj = outPerObj.getObject();

                // Check if Composite Filter is valid
                if (!ArchiveManager.isCompositeFilterValid(compositeFilter, obj)) {
                    throw new IllegalArgumentException();
                }

                // Requirement from the Composite filter: page 57:
                // For the dots: "If a field is nested, it can use the dot to separate"
                try {
                    obj = HelperCOM.getNestedObject(obj, compositeFilter.getFieldName());
                } catch (NoSuchFieldException ex) {
                    // requirement from the Composite filter: page 57
                    // "If the field does not exist in the Composite then the filter shall evaluate to false."
                    continue;
                }

                Element leftHandSide = (Element) HelperAttributes.javaType2Attribute(obj);
                Boolean evaluation = HelperCOM.evaluateExpression(leftHandSide, compositeFilter.getType(), compositeFilter.getFieldValue());

                if (evaluation == null) {
                    continue;
                }

                if (evaluation) {
                    tmpPerObjs.add(outPerObj);
                }
            }

            outPerObjs = tmpPerObjs;
        }

        return outPerObjs;
    }

    protected void generateAndPublishEvents(final ObjectType objType,
            final ArrayList<ArchivePersistenceObject> comObjs, final MALInteraction interaction) {

        if (eventService == null) {
            return;
        }

        final ObjectIdList sourceList = new ObjectIdList();

        for (int i = 0; i < comObjs.size(); i++) {
            final ObjectId source = ArchiveManager.archivePerObj2source(comObjs.get(i));

            // Is the COM Object an Event coming from the archive?
            if (source.getType().equals(HelperCOM.generateCOMObjectType(ArchiveHelper.ARCHIVE_SERVICE, source.getType().getNumber()))) {
                continue; // requirement: 3.4.2.5
            }

            sourceList.add(source);
        }

        if (sourceList.isEmpty()) { // Don't store anything if the list is empty...
            return;
        }

        Logger.getLogger(ArchiveManager.class.getName()).log(Level.FINE, "\nobjType: " + objType.toString() + "\nDomain: " + configuration.getDomain().toString() + "\nSourceList: " + sourceList.toString());
//        Logger.getLogger(ArchiveManager.class.getName()).log(Level.INFO, "\nDomain: " + configuration.getDomain().toString());
//        Logger.getLogger(ArchiveManager.class.getName()).log(Level.INFO, "\nSourceList: " + sourceList.toString());

        // requirement: 3.4.2.4
        final LongList eventObjIds = eventService.generateAndStoreEvents(objType, configuration.getDomain(), null, sourceList, interaction);

        Logger.getLogger(ArchiveManager.class.getName()).log(Level.FINE, "The eventObjIds are: " + eventObjIds.toString());

        URI sourceURI = new URI("");

        if (interaction != null) {
            if (interaction.getMessageHeader() != null) {
                sourceURI = interaction.getMessageHeader().getURITo();
            }
        }

        eventService.publishEvents(sourceURI, eventObjIds, objType, null, sourceList, null);
    }

    protected static ObjectId archivePerObj2source(final ArchivePersistenceObject obj) {
        return new ObjectId(obj.getObjectType(), new ObjectKey(obj.getDomain(), obj.getObjectId()));
    }

    protected static Boolean objectTypeContainsWildcard(final ObjectType objType) {
        return (objType.getArea().getValue() == 0
                || objType.getService().getValue() == 0
                || objType.getVersion().getValue() == 0
                || objType.getNumber().getValue() == 0);
    }

    private static Long objectType2Mask(final ObjectType objType) {

        long areaVal = (objType.getArea().getValue() == 0) ? (long) 0 : (long) 0xFFFF;
        long serviceVal = (objType.getService().getValue() == 0) ? (long) 0 : (long) 0xFFFF;
        long versionVal = (objType.getVersion().getValue() == 0) ? (long) 0 : (long) 0xFF;
        long numberVal = (objType.getNumber().getValue() == 0) ? (long) 0 : (long) 0xFFFF;

        return (new Long(areaVal << 48)
                | new Long(serviceVal << 32)
                | new Long(versionVal << 24)
                | new Long(numberVal));

    }

    public static UIntegerList checkForDuplicates(ArchiveDetailsList archiveDetailsList) {
        UIntegerList dupList = new UIntegerList();

        for (int i = 0; i < archiveDetailsList.size() - 1; i++) {
            if (archiveDetailsList.get(i).getInstId().intValue() == 0) { // Wildcard? Then jump over it
                continue;
            }

            for (int j = i + 1; j < archiveDetailsList.size(); j++) {
                if (archiveDetailsList.get(i).getInstId().intValue() == archiveDetailsList.get(j).getInstId().intValue()) {
                    dupList.add(new UInteger(j));
                }
            }
        }

        return dupList;
    }

    public static boolean isCompositeFilterValid(CompositeFilter compositeFilter, Object obj) {

        if (compositeFilter.getFieldName().contains("\\.")) {  // Looking into a nested field?
            if (!(obj instanceof Composite)) {
                return false;  // If it is not a composite, we can not check fields inside...
            } else {
                try {
                    // Does the Field asked for, exists?
                    HelperCOM.getNestedObject(obj, compositeFilter.getFieldName());

                } catch (NoSuchFieldException ex) {
                    return false;
                }
            }
        }

        ExpressionOperator expressionOperator = compositeFilter.getType();

        if (compositeFilter.getFieldValue() == null) {
            if (expressionOperator.equals(ExpressionOperator.CONTAINS)
                    || expressionOperator.equals(ExpressionOperator.ICONTAINS)
                    || expressionOperator.equals(ExpressionOperator.GREATER)
                    || expressionOperator.equals(ExpressionOperator.GREATER_OR_EQUAL)
                    || expressionOperator.equals(ExpressionOperator.LESS)
                    || expressionOperator.equals(ExpressionOperator.LESS_OR_EQUAL)) {
                return false;
            }
        }

        if (obj instanceof Enumeration) {
            Attribute fieldValue = compositeFilter.getFieldValue();
//            if (!(fieldValue instanceof UInteger) || !(fieldValue.getTypeShortForm() == 11) ) {
            if (!(fieldValue instanceof UInteger)) {
                return false;
            }
        }

        if (obj instanceof Blob) {
            if (!(expressionOperator.equals(ExpressionOperator.EQUAL))
                    && !(expressionOperator.equals(ExpressionOperator.DIFFER))) {
                return false;
            }
        }

        if (expressionOperator.equals(ExpressionOperator.CONTAINS)
                || expressionOperator.equals(ExpressionOperator.ICONTAINS)) {
            if (compositeFilter.getFieldValue().getTypeShortForm() != 15) {  // Is it String?
                return false;
            }
        }

        return true;
    }

}