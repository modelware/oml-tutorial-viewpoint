package io.opencaesar.oml.tutorial.viewpoint;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;

import io.opencaesar.oml.AnnotatedElement;
import io.opencaesar.oml.AnnotationProperty;
import io.opencaesar.oml.Entity;
import io.opencaesar.oml.EntityReference;
import io.opencaesar.oml.ForwardRelation;
import io.opencaesar.oml.LinkAssertion;
import io.opencaesar.oml.Literal;
import io.opencaesar.oml.NamedInstance;
import io.opencaesar.oml.NamedInstanceReference;
import io.opencaesar.oml.OmlFactory;
import io.opencaesar.oml.PropertyValueAssertion;
import io.opencaesar.oml.Reference;
import io.opencaesar.oml.Relation;
import io.opencaesar.oml.RelationCardinalityRestrictionAxiom;
import io.opencaesar.oml.RelationEntity;
import io.opencaesar.oml.RelationInstance;
import io.opencaesar.oml.RelationRangeRestrictionAxiom;
import io.opencaesar.oml.RelationRestrictionAxiom;
import io.opencaesar.oml.RelationTargetRestrictionAxiom;
import io.opencaesar.oml.ReverseRelation;
import io.opencaesar.oml.ScalarProperty;
import io.opencaesar.oml.ScalarPropertyValueAssertion;
import io.opencaesar.oml.Vocabulary;
import io.opencaesar.oml.util.OmlFactory2;
import io.opencaesar.oml.util.OmlRead;
import io.opencaesar.oml.util.OmlSearch;

/**
 * The services class used by VSM.
 */
public class Services {
    
	public static Object getAnnotationByAbbreviatedIri(AnnotatedElement element, String abbreviatedPropertyIri) {
		for (var propertyValue : OmlSearch.findAnnotationValuesForAbbreviatedIri(element, abbreviatedPropertyIri)) {
			return OmlRead.getLiteralValue(propertyValue);
		}
		return null;
	}

	public static void setPropertyByAbbreviatedIri(AnnotatedElement element, String abbreviatedPropertyIri, Object value) {
		if (value.equals("")) value = null; 
		var ontology = OmlRead.getOntology(element);
		var property = OmlRead.getMemberByAbbreviatedIri(ontology, abbreviatedPropertyIri);
		var valueWasSet = false;
		if (property instanceof AnnotationProperty) {
			for (var it = element.getOwnedAnnotations().iterator(); it.hasNext(); ) {
				var annotation = it.next();
				if (annotation.getProperty() == property) {
					if (!valueWasSet && value != null) {
						annotation.setValue(asLiteral(value));
						valueWasSet = true;
					} else {
						it.remove();
					}
				}
			}
			if (!valueWasSet && value != null) {
				var annotation = OmlFactory.eINSTANCE.createAnnotation();
				annotation.setProperty((AnnotationProperty)property);
				annotation.setValue(asLiteral(value));
				element.getOwnedAnnotations().add(annotation);
			}
		} else if (property instanceof ScalarProperty) {
			List<PropertyValueAssertion> propertyValues;
			if (element instanceof NamedInstance) {
				propertyValues = ((NamedInstance)element).getOwnedPropertyValues();
			} else if (element instanceof NamedInstanceReference) {
				propertyValues = ((NamedInstanceReference)element).getOwnedPropertyValues();
			} else {
				System.err.println("Can't set a ScalarProperty on something that isn't a NamedInstance or NamedInstanceReference: " + element);
				return;
			}
			for (var it = propertyValues.iterator(); it.hasNext(); ) {
				var propertyValue = it.next();
				if (propertyValue instanceof ScalarPropertyValueAssertion && ((ScalarPropertyValueAssertion)propertyValue).getProperty() == property) {
					if (!valueWasSet && value != null) {
						((ScalarPropertyValueAssertion)propertyValue).setValue(asLiteral(value));
						valueWasSet = true;
					} else {
						it.remove();
					}
				}
			}
			if (!valueWasSet && value != null) {
				var propertyValue = OmlFactory.eINSTANCE.createScalarPropertyValueAssertion();
				propertyValue.setProperty((ScalarProperty)property);
				propertyValue.setValue(asLiteral(value));
				propertyValues.add(propertyValue);
			}
		} else {
			System.err.println("Not a scalar or annotation property: " + abbreviatedPropertyIri);
		}
	}

	public static Literal asLiteral(Object value) {
		if (value instanceof Double) {
			var literal = OmlFactory.eINSTANCE.createDoubleLiteral();
			literal.setValue((Double)value);
			return literal;
		}
		var literal = OmlFactory.eINSTANCE.createQuotedLiteral();
		literal.setValue(value.toString());
		return literal;
	}


	public static void deleteNamedInstance(NamedInstance instance) {
		for (Reference r : OmlSearch.findReferences(instance)) {
			EcoreUtil.delete(r);
		};
		for (LinkAssertion a : OmlSearch.findLinkAssertionsWithTarget(instance)) {
			EcoreUtil.delete(a);
		};
		for (RelationInstance ri : OmlSearch.findRelationInstancesWithTarget(instance)) {
			deleteNamedInstance(ri);
		};
		for (RelationInstance ri : OmlSearch.findRelationInstancesWithSource(instance)) {
			deleteNamedInstance(ri);
		};
		EcoreUtil.delete(instance);
	}

	public static void deleteLinkByAbbreviatedIri(NamedInstance source, NamedInstance target, String abbreviatedRelationIri) {
		Relation relation = (Relation) OmlRead.getMemberByAbbreviatedIri(source.eResource().getResourceSet(), abbreviatedRelationIri);
		var links = OmlSearch.findLinkAssertionsWithTarget(target).stream().
				filter(a -> a.getRelation() == relation).
				filter(a -> OmlRead.getSource(a) == source).
				collect(Collectors.toList());
		for (var link : links) {
			EcoreUtil.delete(link);
		}
	}

	public static void setForwardRelation(RelationEntity entity, String name) {
		if (name.equals("")) name = null;
		if (entity.getForwardRelation() != null && name == null) {
			EcoreUtil.delete(entity.getForwardRelation());
		} else if (entity.getForwardRelation() == null && name != null) {
			var forward = OmlFactory2.INSTANCE.create(ForwardRelation.class);
			forward.setName(name);
			entity.setForwardRelation(forward);
		} else {
			entity.getForwardRelation().setName(name);
		}
	}
	
	public static void setReverseRelation(RelationEntity entity, String name) {
		if (name.equals("")) name = null;
		if (entity.getReverseRelation() != null && name == null) {
			EcoreUtil.delete(entity.getReverseRelation());
		} else if (entity.getReverseRelation() == null && name != null) {
			var reverse = OmlFactory2.INSTANCE.create(ReverseRelation.class);
			reverse.setName(name);
			entity.setReverseRelation(reverse);
		} else {
			entity.getReverseRelation().setName(name);
		}
	}

	public static Set<Entity> getVisualizedEntities(Vocabulary vocabulary) {
		var entities = new HashSet<Entity>();
		// direct entities
		entities.addAll(vocabulary.getOwnedStatements().stream().
				filter(s -> s instanceof Entity).
				map(s -> (Entity)s).
				collect(Collectors.toSet()));
		// reference entities
		entities.addAll(vocabulary.getOwnedStatements().stream().
				filter(s -> s instanceof EntityReference).
				map(s -> (Entity) OmlRead.resolve((EntityReference)s)).
				collect(Collectors.toSet()));
		// specialized entities
		entities.addAll(vocabulary.getOwnedStatements().stream().
				filter(s -> s instanceof Entity).
				map(s -> (Entity)s).
				flatMap(e -> e.getOwnedSpecializations().stream()).
				map(s -> (Entity) s.getSpecializedTerm()).
				collect(Collectors.toSet()));
		entities.addAll(vocabulary.getOwnedStatements().stream().
				filter(s -> s instanceof EntityReference).
				map(s -> (EntityReference)s).
				flatMap(e -> e.getOwnedSpecializations().stream()).
				map(s -> (Entity) s.getSpecializedTerm()).
				collect(Collectors.toSet()));
		// related entities
		entities.addAll(vocabulary.getOwnedStatements().stream().
				filter(e -> e instanceof RelationEntity).
				map(e -> (RelationEntity)e).
				flatMap(r -> Stream.of(r.getSource(), r.getTarget())).
				collect(Collectors.toSet()));
		// range restricted entities
		entities.addAll(vocabulary.getOwnedStatements().stream().
				filter(s -> s instanceof Entity).
				map(s -> (Entity)s).
				flatMap(e -> e.getOwnedRelationRestrictions().stream()).
				filter(r -> r instanceof RelationRangeRestrictionAxiom).
				map(r -> ((RelationRangeRestrictionAxiom)r).getRange()).
				collect(Collectors.toSet()));
		entities.addAll(vocabulary.getOwnedStatements().stream().
				filter(s -> s instanceof EntityReference).
				map(s -> (EntityReference)s).
				flatMap(e -> e.getOwnedRelationRestrictions().stream()).
				filter(r -> r instanceof RelationRangeRestrictionAxiom).
				map(r -> ((RelationRangeRestrictionAxiom)r).getRange()).
				collect(Collectors.toSet()));
		// cardinality range restricted entities
		entities.addAll(vocabulary.getOwnedStatements().stream().
				filter(s -> s instanceof Entity).
				map(s -> (Entity)s).
				flatMap(e -> e.getOwnedRelationRestrictions().stream()).
				filter(r -> r instanceof RelationCardinalityRestrictionAxiom).
				map(r -> ((RelationCardinalityRestrictionAxiom)r).getRange()).
				collect(Collectors.toSet()));
		entities.addAll(vocabulary.getOwnedStatements().stream().
				filter(s -> s instanceof EntityReference).
				map(s -> (EntityReference)s).
				flatMap(e -> e.getOwnedRelationRestrictions().stream()).
				filter(r -> r instanceof RelationCardinalityRestrictionAxiom).
				map(r -> ((RelationCardinalityRestrictionAxiom)r).getRange()).
				collect(Collectors.toSet()));
		return entities;
	}

	public static Set<NamedInstance> getVisualizedNamedInstances(Vocabulary vocabulary) {
		var instances = new HashSet<NamedInstance>();
		// target restricted instances
		instances.addAll(vocabulary.getOwnedStatements().stream().
				filter(s -> s instanceof Entity).
				map(s -> (Entity)s).
				flatMap(e -> e.getOwnedRelationRestrictions().stream()).
				filter(r -> r instanceof RelationTargetRestrictionAxiom).
				map(r -> ((RelationTargetRestrictionAxiom)r).getTarget()).
				collect(Collectors.toSet()));
		instances.addAll(vocabulary.getOwnedStatements().stream().
				filter(s -> s instanceof EntityReference).
				map(s -> (EntityReference)s).
				flatMap(e -> e.getOwnedRelationRestrictions().stream()).
				filter(r -> r instanceof RelationTargetRestrictionAxiom).
				map(r -> ((RelationTargetRestrictionAxiom)r).getTarget()).
				collect(Collectors.toSet()));
		return instances;
	}
	
	public static Set<RelationRestrictionAxiom> getVisualizedRestrictions(Vocabulary vocabulary) {
		var restrictions = new HashSet<RelationRestrictionAxiom>();
		restrictions.addAll(vocabulary.getOwnedStatements().stream().
				filter(s -> s instanceof Entity).
				map(s -> (Entity)s).
				flatMap(e -> e.getOwnedRelationRestrictions().stream()).
				collect(Collectors.toSet()));
		restrictions.addAll(vocabulary.getOwnedStatements().stream().
				filter(s -> s instanceof EntityReference).
				map(s -> (EntityReference) s).
				flatMap(e -> e.getOwnedRelationRestrictions().stream()).
				collect(Collectors.toSet()));
		return restrictions;
	}

	public EObject openTextEditor(EObject any) {
		if (any != null && any.eResource() instanceof XtextResource && any.eResource().getURI() != null) {

			String fileURI = any.eResource().getURI().toPlatformString(true);
			IFile workspaceFile = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(fileURI));
			if (workspaceFile != null) {
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				try {
					IEditorPart openEditor = IDE.openEditor(page, workspaceFile, "io.opencaesar.oml.dsl.Oml", true);
					if (openEditor instanceof AbstractTextEditor) {
						ICompositeNode node = NodeModelUtils.findActualNodeFor(any);
						if (node != null) {
							int offset = node.getOffset();
							int length = node.getTotalEndOffset() - offset;
							((AbstractTextEditor) openEditor).selectAndReveal(offset, length);
							System.out.println("Services.openTextEditor()");
						}
					}
					// editorInput.
				} catch (PartInitException e) {
					// Put your exception handler here if you wish to.
				}
			}
		}
		System.out.println(any);
		return any;
	}

}
