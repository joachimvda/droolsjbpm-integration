package org.drools.container.spring.namespace;

import static org.drools.container.spring.namespace.DefinitionParserHelper.emptyAttributeCheck;

import java.util.List;

import org.drools.command.CommandFactory;
import org.drools.command.runtime.SetGlobalCommand;
import org.drools.command.runtime.process.SignalEventCommand;
import org.drools.command.runtime.process.StartProcessCommand;
import org.drools.command.runtime.rule.FireAllRulesCommand;
import org.drools.command.runtime.rule.FireUntilHaltCommand;
import org.drools.command.runtime.rule.InsertObjectCommand;
import org.drools.container.spring.beans.StatefulKnowledgeSessionBeanFactory;
import org.drools.container.spring.beans.StatelessKnowledgeSessionBeanFactory;
import org.drools.container.spring.beans.StatefulKnowledgeSessionBeanFactory.JpaConfiguration;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class KnowledgeSessionDefinitionParser extends AbstractBeanDefinitionParser {
    
    private static final String KBASE_ATTRIBUTE          = "kbase";
    private static final String EMF_ATTRIBUTE            = "entity-manager-factory";
    private static final String TX_MANAGER_ATTRIBUTE     = "transaction-manager";
    private static final String FORCLASS_ATTRIBUTE       = "for-class";
    private static final String IMPLEMENTATION_ATTRIBUTE = "implementation";    

    private static final String NAME_ATTRIBUTE           = "name";
    private static final String EXECUTION_NODE_ATTRIBUTE = "node";
    private static final String TYPE_ATTRIBUTE           = "type";

    protected AbstractBeanDefinition parseInternal(Element element,
                                                   ParserContext parserContext) {
        String kbase = element.getAttribute( KBASE_ATTRIBUTE );
        emptyAttributeCheck( element.getLocalName(),
                             KBASE_ATTRIBUTE,
                             kbase );

        String sessionType = element.getAttribute( TYPE_ATTRIBUTE );
        BeanDefinitionBuilder factory;

        if ( "stateful".equals( sessionType ) ) {
            factory = BeanDefinitionBuilder.rootBeanDefinition( StatefulKnowledgeSessionBeanFactory.class );
        } else if ( "stateless".equals( sessionType ) ) {
            factory = BeanDefinitionBuilder.rootBeanDefinition( StatelessKnowledgeSessionBeanFactory.class );
        } else {
            throw new IllegalArgumentException( "Invalid value for " + TYPE_ATTRIBUTE + " attribute: " + sessionType );
        }

        factory.addPropertyReference( "kbase",
                                      kbase );

        String node = element.getAttribute( EXECUTION_NODE_ATTRIBUTE );
        if ( node != null && node.length() > 0 ) {
            factory.addPropertyReference( "node",
                                          node );
        }

        String name = element.getAttribute( NAME_ATTRIBUTE );
        if ( name != null && name.length() > 0 ) {
            factory.addPropertyValue( "name",
                                      name );
        }
        
        Element persistenceElm = DomUtils.getChildElementByTagName(element, "jpa-persistence");
        if ( persistenceElm != null) {
            BeanDefinitionBuilder beanBuilder = BeanDefinitionBuilder.genericBeanDefinition( JpaConfiguration.class );
            
            String loadId = persistenceElm.getAttribute( "load" );
            if ( StringUtils.hasText( loadId ) ) {
                beanBuilder.addPropertyValue( "id", Long.parseLong( loadId ) );
            }
            
            Element tnxMng = DomUtils.getChildElementByTagName(persistenceElm, TX_MANAGER_ATTRIBUTE);
            String ref = tnxMng.getAttribute( "ref" );
            
            beanBuilder.addPropertyReference( "platformTransactionManager", ref );
            
            Element emf = DomUtils.getChildElementByTagName(persistenceElm, EMF_ATTRIBUTE);
            ref = emf.getAttribute( "ref" );
            beanBuilder.addPropertyReference( "entityManagerFactory", ref ); 
            
            Element variablePersisters = DomUtils.getChildElementByTagName(persistenceElm, "variable-persisters");
            if ( variablePersisters != null && variablePersisters.hasChildNodes() ) {
                List<Element> childPersisterElems = DomUtils.getChildElementsByTagName(variablePersisters, "persister");
                ManagedMap persistors = new ManagedMap( childPersisterElems.size() );
                for ( Element persisterElem : childPersisterElems) {
                    String forClass = persisterElem.getAttribute( FORCLASS_ATTRIBUTE );
                    String implementation = persisterElem.getAttribute( IMPLEMENTATION_ATTRIBUTE );
                    if ( !StringUtils.hasText( forClass ) ) {                        
                        throw new RuntimeException( "persister element must have valid for-class attribute" );
                    }
                    if ( !StringUtils.hasText( implementation ) ) {                        
                        throw new RuntimeException( "persister element must have valid implementation attribute" );
                    }                    
                    persistors.put( forClass,
                                    implementation );                    
                }
                beanBuilder.addPropertyValue( "variablePersisters", persistors );
            }
            
            factory.addPropertyValue( "jpaConfiguration", beanBuilder.getBeanDefinition() );
            
        }
        
        Element script = DomUtils.getChildElementByTagName(element, "script");
        if ( script != null) {
            // we know there can only ever be one
            ManagedList children = new ManagedList();

            for (int i = 0, length = script.getChildNodes().getLength(); i < length; i++) {
                Node n = script.getChildNodes().item( i );
                if ( n instanceof Element ) {
                    Element e = ( Element ) n;
                    
                    BeanDefinitionBuilder beanBuilder = null;
                    if ( "insert-object".equals( e.getLocalName() ) ) {
                        String ref = e.getAttribute( "ref" );
                        Element nestedElm = getFirstElement( e.getChildNodes() );                   
                        beanBuilder = BeanDefinitionBuilder.genericBeanDefinition( InsertObjectCommand.class );                        
                        if ( StringUtils.hasText( ref ) ) {
                            beanBuilder.addConstructorArgReference( ref );
                        } else if ( nestedElm != null  ) {
                            beanBuilder.addConstructorArg( parserContext.getDelegate().parseBeanDefinitionElement( nestedElm ) );                         
                        } else {
                            throw new IllegalArgumentException( "insert-object must either specify a 'ref' attribute or have a nested bean" );
                        }
                    } else if ( "set-global".equals( e.getLocalName() ) ) {
                        String ref = e.getAttribute( "ref" );
                        Element nestedElm = getFirstElement( e.getChildNodes() );                   
                        beanBuilder = BeanDefinitionBuilder.genericBeanDefinition( SetGlobalCommand.class );
                        beanBuilder.addConstructorArg( e.getAttribute( "identifier" ) );
                        if ( StringUtils.hasText( ref ) ) {
                            beanBuilder.addConstructorArgReference( ref );
                        } else if ( nestedElm != null  ) {
                            beanBuilder.addConstructorArg( parserContext.getDelegate().parseBeanDefinitionElement( nestedElm ) );                         
                        } else {
                            throw new IllegalArgumentException( "set-global must either specify a 'ref' attribute or have a nested bean" );
                        }
                    }  else if ( "fire-until-halt".equals( e.getLocalName() ) ) {
                        beanBuilder = BeanDefinitionBuilder.genericBeanDefinition( FireUntilHaltCommand.class );
                    }  else if ( "fire-all-rules".equals( e.getLocalName() ) ) {
                        beanBuilder = BeanDefinitionBuilder.genericBeanDefinition( FireAllRulesCommand.class );
                        String max = e.getAttribute( "max" );
                        if ( StringUtils.hasText( max ) ) {
                            beanBuilder.addPropertyValue( "max", max );
                        }
                    }  else if ( "start-process".equals( e.getLocalName() ) ) {
                        
                        beanBuilder = BeanDefinitionBuilder.genericBeanDefinition( StartProcessCommand.class );
                        String processId = e.getAttribute( "process-id" );
                        if ( !StringUtils.hasText( processId ) ) {
                            throw new IllegalArgumentException( "start-process must specify a process-id" );
                        }
                        beanBuilder.addConstructorArg( processId );
                        
                        List<Element> params = DomUtils.getChildElementsByTagName( e, "parameter" );
                        if ( !params.isEmpty() ) {
                            ManagedMap map = new ManagedMap();
                            for ( Element param : params ) {
                                String identifier = param.getAttribute( "identifier" );
                                if ( !StringUtils.hasText( identifier ) ) {
                                    throw new IllegalArgumentException( "start-process paramaters must specify an identifier" );
                                }
                                
                                String ref = param.getAttribute( "ref" );
                                Element nestedElm = getFirstElement( param.getChildNodes() );  
                                if ( StringUtils.hasText( ref ) ) {
                                    map.put( identifier,  new RuntimeBeanReference( ref) );
                                } else if ( nestedElm != null  ) {
                                    map.put( identifier,  parserContext.getDelegate().parseBeanDefinitionElement( nestedElm ) );
                                } else {
                                    throw new IllegalArgumentException( "start-process paramaters must either specify a 'ref' attribute or have a nested bean" );
                                }                                    
                            }   
                            beanBuilder.addPropertyValue( "parameters", map );
                        }
                    } else if ( "signal-event".equals( e.getLocalName() ) ) {
                        beanBuilder = BeanDefinitionBuilder.genericBeanDefinition( SignalEventCommand.class );
                        String processInstanceId = e.getAttribute( "process-instance-id" );
                        if ( StringUtils.hasText( processInstanceId ) ) {
                            beanBuilder.addConstructorArg( processInstanceId );    
                        }
                        
                        beanBuilder.addConstructorArg( e.getAttribute( "event-type" ) );
                        
                        String ref = e.getAttribute( "ref" );
                        Element nestedElm = getFirstElement( e.getChildNodes() );                   
                        if ( StringUtils.hasText( ref ) ) {
                            beanBuilder.addConstructorArgReference( ref );
                        } else if ( nestedElm != null  ) {
                            beanBuilder.addConstructorArg( parserContext.getDelegate().parseBeanDefinitionElement( nestedElm ) );                         
                        } else {
                            throw new IllegalArgumentException( "signal-event must either specify a 'ref' attribute or have a nested bean" );
                        }             
                    }
                    children.add( beanBuilder.getBeanDefinition() );
                }
            }
            factory.addPropertyValue( "script", children );
        }        

        return factory.getBeanDefinition();
    }
    
    private Element getFirstElement(NodeList list) {                    
        for (int j = 0, lengthj = list.getLength(); j < lengthj; j++) {
            if ( list.item( j ) instanceof Element ) {
                return ( Element ) list.item( j );
            }
        }   
        return null;
    }

}
