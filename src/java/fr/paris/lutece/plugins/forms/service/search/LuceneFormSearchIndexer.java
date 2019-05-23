/*
 * Copyright (c) 2002-2017, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.forms.service.search;

import fr.paris.lutece.plugins.forms.business.Form;
import fr.paris.lutece.plugins.forms.business.FormHome;
import fr.paris.lutece.plugins.forms.business.FormQuestionResponse;
import fr.paris.lutece.plugins.forms.business.FormResponse;
import fr.paris.lutece.plugins.forms.business.FormResponseHome;
import fr.paris.lutece.plugins.forms.business.FormResponseStep;
import fr.paris.lutece.plugins.forms.business.form.search.FormResponseSearchItem;
import fr.paris.lutece.plugins.forms.business.form.search.IndexerAction;
import fr.paris.lutece.plugins.forms.service.FormsPlugin;
import fr.paris.lutece.plugins.genericattributes.business.Entry;
import fr.paris.lutece.plugins.genericattributes.business.Response;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.EntryTypeServiceManager;
import fr.paris.lutece.plugins.workflowcore.business.state.State;
import fr.paris.lutece.plugins.workflowcore.service.state.IStateService;
import fr.paris.lutece.plugins.workflowcore.service.state.StateService;
import fr.paris.lutece.plugins.workflowcore.service.workflow.WorkflowService;
import fr.paris.lutece.portal.service.content.XPageAppService;
import fr.paris.lutece.portal.service.message.SiteMessageException;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.search.IndexationService;
import fr.paris.lutece.portal.service.spring.SpringContextService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPathService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.util.url.UrlItem;

import org.apache.commons.lang.StringUtils;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;

import java.io.IOException;
import java.math.BigInteger;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;
import org.jsoup.Jsoup;

/**
 * Forms global indexer
 */
public class LuceneFormSearchIndexer extends AbstractFormSearchIndexer
{
    public static final String INDEXER_NAME = "FormsIndexer";
    private static final String INDEXER_DESCRIPTION = "Indexer service for forms";
    private static final String FORMS = "forms";
    private static final String INDEXER_VERSION = "1.0.0";
    private static final String PROPERTY_INDEXER_ENABLE = "forms.globalIndexer.enable";

    @Inject
    private WorkflowService _workflowService;
    @Inject
    private LuceneFormSearchFactory _luceneFormSearchFactory;
    private IndexWriter _indexWriter;

    public LuceneFormSearchIndexer( )
    {
        IndexationService.registerIndexer( this );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName( )
    {
        return INDEXER_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription( )
    {
        return INDEXER_DESCRIPTION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getVersion( )
    {
        return INDEXER_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnable( )
    {
        return AppPropertiesService.getPropertyBoolean( PROPERTY_INDEXER_ENABLE, false );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getListType( )
    {
        List<String> listType = new ArrayList<String>( 1 );
        listType.add( FORMS );

        return listType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSpecificSearchAppUrl( )
    {
        UrlItem url = new UrlItem( AppPathService.getPortalUrl( ) );
        url.addParameter( XPageAppService.PARAM_XPAGE_APP, FORMS );

        return url.getUrl( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Document> getDocuments( String formResponseId ) throws IOException, InterruptedException, SiteMessageException
    {

        int nIdFormResponse;

        try
        {
            nIdFormResponse = Integer.parseInt( formResponseId );
        }
        catch( NumberFormatException ne )
        {
            AppLogService.error( formResponseId + " not parseable to an int", ne );

            return new ArrayList<>( 0 );
        }

        FormResponse formResponse = FormResponseHome.findByPrimaryKey( nIdFormResponse );
        Form form = FormHome.findByPrimaryKey( formResponse.getFormId( ) );
        State formResponseState = _workflowService.getState( formResponse.getId(), FormResponse.RESOURCE_TYPE, form.getIdWorkflow(), -1);
        

        Document doc = getDocument( formResponse, form, formResponseState );
        if ( doc != null )
        {
            List<Document> listDocument = new ArrayList<>( 1 );
            listDocument.add( doc );

            return listDocument;
        }

        return new ArrayList<>( 0 );
    }

    /**
     * Builds a document which will be used by Lucene during the indexing of this record
     * 
     * @param formResponse
     *            the formResponse object
     * @return a lucene document filled with the record data
     */
    public Document getDocument( FormResponse formResponse, Form form, State formResponseState )
    {


        // make a new, empty document
        Document doc = new Document( );

        int nIdFormResponse = formResponse.getId( );

        // --- document identifier
        doc.add( new StringField( FormResponseSearchItem.FIELD_UID, String.valueOf( nIdFormResponse ), Field.Store.YES ) );

        // --- form response identifier
        doc.add( new IntPoint( FormResponseSearchItem.FIELD_ID_FORM_RESPONSE, nIdFormResponse ) );
        doc.add( new NumericDocValuesField( FormResponseSearchItem.FIELD_ID_FORM_RESPONSE, nIdFormResponse ) );
        doc.add( new StoredField( FormResponseSearchItem.FIELD_ID_FORM_RESPONSE, nIdFormResponse ) );

        // --- field contents
        doc.add( new TextField( FormResponseSearchItem.FIELD_CONTENTS, manageNullValue( getContentToIndex(formResponse ) ), Field.Store.NO ) );

        // --- form title
        String strFormTitle = manageNullValue( form.getTitle( ) );
        doc.add( new StringField( FormResponseSearchItem.FIELD_FORM_TITLE, strFormTitle, Field.Store.YES ) );
        doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_FORM_TITLE, new BytesRef( strFormTitle ) ) );

        // --- id form
        doc.add( new IntPoint( FormResponseSearchItem.FIELD_ID_FORM, form.getId( ) ) );
        doc.add( new NumericDocValuesField( FormResponseSearchItem.FIELD_ID_FORM, form.getId( ) ) );
        doc.add( new StoredField( FormResponseSearchItem.FIELD_ID_FORM_RESPONSE, form.getId( ) ) );

        // --- form response date create
        Long longCreationDate = formResponse.getCreation().getTime();
        doc.add( new LongPoint( FormResponseSearchItem.FIELD_DATE_CREATION, longCreationDate ) );
        doc.add( new NumericDocValuesField( FormResponseSearchItem.FIELD_DATE_CREATION, longCreationDate ) );
        doc.add( new StoredField( FormResponseSearchItem.FIELD_DATE_CREATION, longCreationDate ) );

        // --- form response date closure
        Long longUpdateDate = formResponse.getUpdate( ).getTime();
        doc.add( new LongPoint( FormResponseSearchItem.FIELD_DATE_UPDATE, longUpdateDate ) );
        doc.add( new NumericDocValuesField( FormResponseSearchItem.FIELD_DATE_UPDATE, longUpdateDate ) );
        doc.add( new StoredField( FormResponseSearchItem.FIELD_DATE_UPDATE, longUpdateDate ) );

        // --- id form response workflow state
        int nIdFormResponseWorkflowState = formResponseState.getId( ) ;
        doc.add( new IntPoint( FormResponseSearchItem.FIELD_ID_WORKFLOW_STATE, nIdFormResponseWorkflowState ) );
        doc.add( new NumericDocValuesField( FormResponseSearchItem.FIELD_ID_WORKFLOW_STATE, nIdFormResponseWorkflowState ) );
        doc.add( new StoredField( FormResponseSearchItem.FIELD_ID_WORKFLOW_STATE, nIdFormResponseWorkflowState ) );
        
        // --- form response workflow state title
        String strFormResponseWorkflowStateTitle = manageNullValue( formResponseState.getName( ) );
        doc.add( new StringField( FormResponseSearchItem.FIELD_FORM_TITLE, strFormResponseWorkflowStateTitle, Field.Store.YES ) );
        doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_FORM_TITLE, new BytesRef( strFormResponseWorkflowStateTitle ) ) );
        
         // --- ticket user Message
        String userMessage = ticket.getUserMessage( ) == null ? "" : Jsoup.parse( ticket.getUserMessage( ) ).text( );
        doc.add( new StringField( FormResponseSearchItem.FIELD_ID_WORKFLOW_STATE, userMessage, Field.Store.YES ) );
        doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_USER_MESSAGE, new BytesRef( userMessage ) ) );
        
        // --- ticket user Guid
        String userGuid = ticket.getGuid()== null ? "" : Jsoup.parse( ticket.getGuid( ) ).text( );
        doc.add( new StringField( FormResponseSearchItem.FIELD_USER_GUID, userGuid, Field.Store.YES ) );
        doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_USER_MESSAGE, new BytesRef( userGuid ) ) );
        
        // --- ticket user Message
        String userMessage = ticket.getUserMessage( ) == null ? "" : Jsoup.parse( ticket.getUserMessage( ) ).text( );
        doc.add( new StringField( FormResponseSearchItem.FIELD_USER_MESSAGE, userMessage, Field.Store.YES ) );
        doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_USER_MESSAGE, new BytesRef( userMessage ) ) );

        // --- ticket Facil'familles
        String strFacilFamille = ticket.getFacilFamilleNumber( ) != null ? ticket.getFacilFamilleNumber( ) : "";
        doc.add( new StringField( FormResponseSearchItem.FIELD_FACIL_FAMILLE, strFacilFamille, Field.Store.YES ) );
        doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_FACIL_FAMILLE, new BytesRef( strFacilFamille ) ) );

        // --- ticket comment
        doc.add( new StoredField( FormResponseSearchItem.FIELD_COMMENT, manageNullValue( ticket.getTicketComment( ) ) ) );

        // --- ticket nomenclature
        String strNomenclature = manageNullValue( ticket.getNomenclature( ) );
        doc.add( new TextField( FormResponseSearchItem.FIELD_TICKET_NOMENCLATURE, strNomenclature, Field.Store.YES ) );
        doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_TICKET_NOMENCLATURE, new BytesRef( strNomenclature ) ) );

        // --- ticket criticality
        int nCriticality = ticket.getCriticality( );
        doc.add( new IntPoint( FormResponseSearchItem.FIELD_CRITICALITY, nCriticality ) );
        doc.add( new StoredField( FormResponseSearchItem.FIELD_CRITICALITY, nCriticality ) );

        // --- ticket priority
        int nPriority = ticket.getPriority( );
        doc.add( new IntPoint( FormResponseSearchItem.FIELD_PRIORITY, nPriority ) );
        doc.add( new StoredField( FormResponseSearchItem.FIELD_PRIORITY, nPriority ) );

        // --- ticket status
        doc.add( new StoredField( FormResponseSearchItem.FIELD_STATUS, ticket.getTicketStatus( ) ) );

        // --- ticket category
        for ( TicketCategory ticketCategory : ticket.getBranch( ) )
        {
            doc.add( new IntPoint( FormResponseSearchItem.FIELD_CATEGORY_ID_DEPTHNUMBER + ticketCategory.getDepth( ).getDepthNumber( ), ticketCategory.getId( ) ) );
            doc.add( new StoredField( FormResponseSearchItem.FIELD_CATEGORY_ID_DEPTHNUMBER + ticketCategory.getDepth( ).getDepthNumber( ), ticketCategory.getId( ) ) );
            doc.add( new StringField( FormResponseSearchItem.FIELD_CATEGORY_ID_DEPTHNUMBER + ticketCategory.getDepth( ).getDepthNumber( ), Integer.toString( ticketCategory.getId( ) ), Field.Store.YES ) );
            doc.add( new TextField( FormResponseSearchItem.FIELD_CATEGORY_DEPTHNUMBER + ticketCategory.getDepth( ).getDepthNumber( ), ticketCategory.getLabel( ), Field.Store.YES ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_CATEGORY_DEPTHNUMBER + ticketCategory.getDepth( ).getDepthNumber( ), new BytesRef( ticketCategory.getLabel( ) ) ) );
            doc.add( new StringField( FormResponseSearchItem.FIELD_CATEGORY_DEPTHNUMBER + ticketCategory.getDepth( ).getDepthNumber( ), manageNullValue( ticketCategory.getLabel( ) ), Field.Store.YES ) );
        }

        // --- ticket user title
        doc.add( new StoredField( FormResponseSearchItem.FIELD_USER_TITLE, manageNullValue( ticket.getUserTitle( ) ) ) );

        // --- ticket user firstname
        String strFirstname = manageNullValue( ticket.getFirstname( ) );
        doc.add( new TextField( FormResponseSearchItem.FIELD_FIRSTNAME, strFirstname, Field.Store.YES ) );
        doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_FIRSTNAME, new BytesRef( strFirstname ) ) );

        // --- ticket user lastname
        String strLastName = manageNullValue( ticket.getLastname( ) );
        doc.add( new TextField( FormResponseSearchItem.FIELD_LASTNAME, strLastName, Field.Store.YES ) );
        doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_LASTNAME, new BytesRef( strLastName ) ) );

        // --- ticket user email
        String strEmail = manageNullValue( ticket.getEmail( ) );
        doc.add( new StringField( FormResponseSearchItem.FIELD_EMAIL, strEmail, Field.Store.YES ) );
        doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_EMAIL, new BytesRef( strEmail ) ) );

        // --- ticket phone number
        doc.add( new StoredField( FormResponseSearchItem.FIELD_MOBILE_PHONE_NUMBER, manageNullValue( ticket.getMobilePhoneNumber( ) ) ) );
        doc.add( new StoredField( FormResponseSearchItem.FIELD_FIXED_PHONE_NUMBER, manageNullValue( ticket.getFixedPhoneNumber( ) ) ) );

        // --- ticket state
        if ( ticket.getState( ) != null )
        {
            int nIdState = ticket.getState( ).getId( );
            doc.add( new IntPoint( FormResponseSearchItem.FIELD_STATE_ID, nIdState ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_STATE_ID, TicketSearchUtil.getBytesRef( BigInteger.valueOf( nIdState ) ) ) );
            doc.add( new StoredField( FormResponseSearchItem.FIELD_STATE_ID, nIdState ) );

            String strState = manageNullValue( ticket.getState( ).getName( ) );
            doc.add( new TextField( FormResponseSearchItem.FIELD_STATE, strState, Field.Store.YES ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_STATE, new BytesRef( strState ) ) );
        } else
        {
            doc.add( new IntPoint( FormResponseSearchItem.FIELD_STATE_ID, CONSTANT_ID_NULL ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_STATE_ID, TicketSearchUtil.getBytesRef( BigInteger.valueOf( CONSTANT_ID_NULL ) ) ) );
            doc.add( new StoredField( FormResponseSearchItem.FIELD_STATE_ID, CONSTANT_ID_NULL ) );

            doc.add( new TextField( FormResponseSearchItem.FIELD_STATE, StringUtils.EMPTY, Field.Store.YES ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_STATE, new BytesRef( StringUtils.EMPTY ) ) );
        }

        // --- ticket channel
        if ( ticket.getChannel( ) != null )
        {
            doc.add( new StoredField( FormResponseSearchItem.FIELD_CHANNEL_ICONFONT, manageNullValue( ticket.getChannel( ).getIconFont( ) ) ) );

            String strChannelLabel = manageNullValue( ticket.getChannel( ).getLabel( ) );
            doc.add( new TextField( FormResponseSearchItem.FIELD_CHANNEL_LABEL, strChannelLabel, Field.Store.YES ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_CHANNEL_LABEL, new BytesRef( strChannelLabel ) ) );
        } else
        {
            doc.add( new StoredField( FormResponseSearchItem.FIELD_CHANNEL_ICONFONT, StringUtils.EMPTY ) );

            doc.add( new TextField( FormResponseSearchItem.FIELD_CHANNEL_LABEL, StringUtils.EMPTY, Field.Store.YES ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_CHANNEL_LABEL, new BytesRef( StringUtils.EMPTY ) ) );
        }

        // --- ticket assigneeUnit
        if ( ticket.getAssigneeUnit( ) != null )
        {
            int nAssigneeUnitId = ticket.getAssigneeUnit( ).getUnitId( );
            doc.add( new IntPoint( FormResponseSearchItem.FIELD_ASSIGNEE_UNIT_ID, nAssigneeUnitId ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_ASSIGNEE_UNIT_ID, TicketSearchUtil.getBytesRef( BigInteger.valueOf( nAssigneeUnitId ) ) ) );
            doc.add( new StoredField( FormResponseSearchItem.FIELD_ASSIGNEE_UNIT_ID, nAssigneeUnitId ) );

            String strAssigneeUnitName = manageNullValue( ticket.getAssigneeUnit( ).getName( ) );
            doc.add( new TextField( FormResponseSearchItem.FIELD_ASSIGNEE_UNIT_NAME, strAssigneeUnitName, Field.Store.YES ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_ASSIGNEE_UNIT_NAME, new BytesRef( strAssigneeUnitName ) ) );
        } else
        {
            doc.add( new IntPoint( FormResponseSearchItem.FIELD_ASSIGNEE_UNIT_ID, CONSTANT_ID_NULL ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_ASSIGNEE_UNIT_ID, TicketSearchUtil.getBytesRef( BigInteger.valueOf( CONSTANT_ID_NULL ) ) ) );
            doc.add( new StoredField( FormResponseSearchItem.FIELD_ASSIGNEE_UNIT_ID, CONSTANT_ID_NULL ) );

            doc.add( new TextField( FormResponseSearchItem.FIELD_ASSIGNEE_UNIT_NAME, StringUtils.EMPTY, Field.Store.YES ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_ASSIGNEE_UNIT_NAME, new BytesRef( StringUtils.EMPTY ) ) );
        }

        // --- ticket assigneeUser
        if ( ticket.getAssigneeUser( ) != null )
        {
            int nAdminUserId = ticket.getAssigneeUser( ).getAdminUserId( );
            doc.add( new IntPoint( FormResponseSearchItem.FIELD_ASSIGNEE_USER_ADMIN_ID, nAdminUserId ) );
            doc.add( new StoredField( FormResponseSearchItem.FIELD_ASSIGNEE_USER_ADMIN_ID, nAdminUserId ) );

            doc.add( new StoredField( FormResponseSearchItem.FIELD_ASSIGNEE_USER_FIRSTNAME, manageNullValue( ticket.getAssigneeUser( ).getFirstname( ) ) ) );

            String strAssigneeUserLastname = manageNullValue( ticket.getAssigneeUser( ).getLastname( ) );
            doc.add( new TextField( FormResponseSearchItem.FIELD_ASSIGNEE_USER_LASTNAME, strAssigneeUserLastname, Field.Store.YES ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_ASSIGNEE_USER_LASTNAME, new BytesRef( strAssigneeUserLastname ) ) );
        } else
        {
            doc.add( new IntPoint( FormResponseSearchItem.FIELD_ASSIGNEE_USER_ADMIN_ID, CONSTANT_ID_NULL ) );
            doc.add( new StoredField( FormResponseSearchItem.FIELD_ASSIGNEE_USER_ADMIN_ID, CONSTANT_ID_NULL ) );

            doc.add( new StoredField( FormResponseSearchItem.FIELD_ASSIGNEE_USER_FIRSTNAME, StringUtils.EMPTY ) );

            doc.add( new TextField( FormResponseSearchItem.FIELD_ASSIGNEE_USER_LASTNAME, StringUtils.EMPTY, Field.Store.YES ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_ASSIGNEE_USER_LASTNAME, new BytesRef( StringUtils.EMPTY ) ) );
        }

        // --- ticket assignerUnit
        if ( ticket.getAssignerUnit( ) != null )
        {
            int nAssignerUnitId = ticket.getAssignerUnit( ).getUnitId( );
            doc.add( new IntPoint( FormResponseSearchItem.FIELD_ASSIGNER_UNIT_ID, nAssignerUnitId ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_ASSIGNER_UNIT_ID, TicketSearchUtil.getBytesRef( BigInteger.valueOf( nAssignerUnitId ) ) ) );
            doc.add( new StoredField( FormResponseSearchItem.FIELD_ASSIGNER_UNIT_ID, nAssignerUnitId ) );
        } else
        {
            doc.add( new IntPoint( FormResponseSearchItem.FIELD_ASSIGNER_UNIT_ID, CONSTANT_ID_NULL ) );
            doc.add( new SortedDocValuesField( FormResponseSearchItem.FIELD_ASSIGNER_UNIT_ID, TicketSearchUtil.getBytesRef( BigInteger.valueOf( CONSTANT_ID_NULL ) ) ) );
            doc.add( new StoredField( FormResponseSearchItem.FIELD_ASSIGNER_UNIT_ID, CONSTANT_ID_NULL ) );
        }

        // --- ticket assignerUser
        if ( ticket.getAssignerUser( ) != null )
        {
            int nAssignerUserId = ticket.getAssignerUser( ).getAdminUserId( );
            doc.add( new IntPoint( FormResponseSearchItem.FIELD_ASSIGNER_USER_ID, nAssignerUserId ) );
            doc.add( new StoredField( FormResponseSearchItem.FIELD_ASSIGNER_USER_ID, nAssignerUserId ) );
        } else
        {
            doc.add( new IntPoint( FormResponseSearchItem.FIELD_ASSIGNER_USER_ID, CONSTANT_ID_NULL ) );
            doc.add( new StoredField( FormResponseSearchItem.FIELD_ASSIGNER_USER_ID, CONSTANT_ID_NULL ) );
        }

        // --- ticket read marker Id
        int nMarkingId = ticket.getIdTicketMarking( );
        doc.add( new IntPoint( FormResponseSearchItem.FIELD_TICKET_MARKING_ID, nMarkingId ) );
        doc.add( new StoredField( FormResponseSearchItem.FIELD_TICKET_MARKING_ID, nMarkingId ) );

        return doc;
    }

    /**
     * Concatenates the value of the specified field in this record
     * 
     * @param record
     *            the record to seek
     * @param listEntry
     *            the list of field to concatenate
     * @param plugin
     *            the plugin object
     * @return
     */
    private String getContentToIndex( FormResponse formResponse )
    {

        StringBuilder sb = new StringBuilder( );

        for ( FormResponseStep formResponseStep : formResponse.getSteps( ) )
        {
            for ( FormQuestionResponse questionResponse : formResponseStep.getQuestions( ) )
            {

                // Only index the indexable entries
                if ( questionResponse.getQuestion( ).isResponsesIndexed( ) )
                {
                    Entry entry = questionResponse.getQuestion( ).getEntry( );
                    for ( Response response : questionResponse.getEntryResponse( ) )
                    {

                        String responseString = EntryTypeServiceManager.getEntryTypeService( entry ).getResponseValueForExport( entry, null, response, null );
                        if ( !StringUtils.isEmpty( responseString ) )
                        {
                            sb.append( responseString );
                            sb.append( " " );
                        }
                    }
                }
            }
        }

        return sb.toString( );
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void processIncrementalIndexing( StringBuffer sbLog )
    {
        sbLog = ( sbLog == null ? new StringBuffer( ) : sbLog );
        initIndexing( false );

        Plugin plugin = PluginService.getPlugin( FormsPlugin.PLUGIN_NAME );
        List<Integer> listIdsToAdd = new ArrayList<>( );

        // Delete all record which must be delete
        for ( IndexerAction action : getAllIndexerActionByTask( IndexerAction.TASK_DELETE, plugin ) )
        {
            sbLogFormResponse( sbLog, action.getIdFormResponse( ), IndexerAction.TASK_DELETE );
            try
            {
                _indexWriter.deleteDocuments( new Term( FormResponseSearchItem.FIELD_ID_FORM_RESPONSE, Integer.toString( action.getIdFormResponse( ) ) ) );
            }
            catch( IOException e )
            {
                AppLogService.error( "Unable to delete docs from Lucene index ", e );
                sbLogFormException( sbLog, action.getIdFormResponse( ), "Unable to delete from index" );
            }
            removeIndexerAction( action.getIdAction( ), plugin );
        }

        // Update all record which must be update
        for ( IndexerAction action : getAllIndexerActionByTask( IndexerAction.TASK_MODIFY, plugin ) )
        {
            sbLogFormResponse( sbLog, action.getIdFormResponse( ), IndexerAction.TASK_MODIFY );
            try
            {
                _indexWriter.deleteDocuments( new Term( FormResponseSearchItem.FIELD_ID_FORM_RESPONSE, Integer.toString( action.getIdFormResponse( ) ) ) );
            }
            catch( IOException e )
            {
                AppLogService.error( "Unable to delete docs from Lucene index ", e );
                sbLogFormException( sbLog, action.getIdFormResponse( ), "Unable to delete from index" );
            }
            listIdsToAdd.add( action.getIdFormResponse( ) );
            removeIndexerAction( action.getIdAction( ), plugin );
        }

        // Add all form response which must be add
        for ( IndexerAction action : getAllIndexerActionByTask( IndexerAction.TASK_CREATE, plugin ) )
        {
            sbLogFormResponse( sbLog, action.getIdFormResponse( ), IndexerAction.TASK_CREATE );
            listIdsToAdd.add( action.getIdFormResponse( ) );
            removeIndexerAction( action.getIdAction( ), plugin );
        }

        List<FormResponse> listFormResponses = new ArrayList<>( );
        for ( Integer nIdFormResponse : listIdsToAdd )
        {
            // TODO IMPLEMENT A SQL IN( ..) instead
            listFormResponses.add( FormResponseHome.findByPrimaryKey( nIdFormResponse ) );
        }
        indexFormResponseList( sbLog, listFormResponses );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processFullIndexing( StringBuffer sbLog )
    {
        sbLog = ( sbLog == null ? new StringBuffer( ) : sbLog );
        initIndexing( true );

        // Get the list of all the forms response of all forms
        List<FormResponse> listFormResponses = FormResponseHome.selectAllFormResponses( );

        for ( FormResponse formResponse : listFormResponses )
        {
            sbLogFormResponse( sbLog, formResponse.getId( ), IndexerAction.TASK_CREATE );
        }

        indexFormResponseList( sbLog, listFormResponses );
    }

    @Override
    public void indexFormResponseList( StringBuffer sbLog, List<FormResponse> listFormResponse )
    {
        sbLog = ( sbLog == null ? new StringBuffer( ) : sbLog );

        if ( _indexWriter == null || !_indexWriter.isOpen( ) )
            initIndexing( true );

        for ( FormResponse formResponse : listFormResponse )
        {
            Document doc = null;

            try
            {
                doc = getDocument( formResponse );
            }
            catch( Exception e )
            {
                String strMessage = "FormResponse ID : " + formResponse.getId( );
                IndexationService.error( this, e, strMessage );
            }

            if ( doc != null )
            {
                try
                {
                    _indexWriter.addDocument( doc );
                }
                catch( IOException e )
                {
                    AppLogService.error( "Unable to index form response with id " + formResponse.getId( ), e );
                    sbLogFormException( sbLog, formResponse.getId( ), "Unable to add doc in Lucene index" );
                }
            }
        }

        endIndexing( );
    }

    /**
     * Init the indexing action
     * 
     * @param bCreate
     */
    private void initIndexing( boolean bCreate )
    {
        MutableBoolean boolCreate = new MutableBoolean( bCreate );
        _indexWriter = _luceneFormSearchFactory.getIndexWriter( boolCreate );
    }

    /**
     * End the indexing action
     */
    private void endIndexing( )
    {
        if ( _indexWriter != null )
        {
            try
            {
                _indexWriter.close( );
            }
            catch( IOException e )
            {
                AppLogService.error( "Unable to close index writer ", e );
            }
        }
    }

    /**
     * indexing action performed on the recording
     * 
     * @param sbLogs
     *            the buffer log
     * @param nIdRecord
     *            the id of the record
     * @param nIdDirectory
     *            the id of the directory
     * @param nAction
     *            the indexer action key performed
     */
    private void sbLogFormResponse( StringBuffer sbLogs, int nIdFormResponse, int nAction )
    {
        sbLogs.append( "Indexing Form response :" );

        switch( nAction )
        {
            case IndexerAction.TASK_CREATE:
                sbLogs.append( "Insert " );

                break;

            case IndexerAction.TASK_MODIFY:
                sbLogs.append( "Modify " );

                break;

            case IndexerAction.TASK_DELETE:
                sbLogs.append( "Delete " );

                break;

            default:
                break;
        }

        sbLogs.append( "form_response :" );
        sbLogs.append( "id=" );
        sbLogs.append( nIdFormResponse );
        sbLogs.append( "\r\n" );
    }

    private void sbLogFormException( StringBuffer sbLogs, int nIdFormResponse, String strExceptionMessage )
    {
        sbLogs.append( "Exception occured :\r\n" );
        sbLogs.append( "FormResponse id :\r\n" );
        sbLogs.append( nIdFormResponse );
        sbLogs.append( "FormResponse id :\r\n" );
        sbLogs.append( strExceptionMessage );
        sbLogs.append( "\r\n" );
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void indexDocuments( ) throws IOException, InterruptedException, SiteMessageException
    {
        processFullIndexing( null );
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void deleteIndex( )
    {
        if ( _indexWriter == null || !_indexWriter.isOpen( ) )
            initIndexing( true );
        try
        {
            _indexWriter.deleteAll( );
        }
        catch( IOException e )
        {
            AppLogService.error( "Unable to delete all docs in index ", e );
        }
        finally
        {
            endIndexing( );
        }
    }
    
    private String manageNullValue( String strValue )
    {
        if ( strValue == null ) return StringUtils.EMPTY;
        return strValue;
    }
}
