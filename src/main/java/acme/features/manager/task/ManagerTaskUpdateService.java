/*
 * ManagerTaskUpdateService.java
 *
 * Copyright (C) 2012-2021 Rafael Corchuelo.
 *
 * In keeping with the traditional purpose of furthering education and research, it is
 * the policy of the copyright owner to permit non-commercial use and redistribution of
 * this software. It has been tested carefully, but it is not guaranteed for any particular
 * purposes. The copyright owner does not offer any warranties or representations, nor do
 * they accept any liabilities with respect to them.
 */

package acme.features.manager.task;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import acme.entities.roles.Manager;
import acme.entities.tasks.Task;
import acme.framework.components.Errors;
import acme.framework.components.HttpMethod;
import acme.framework.components.Model;
import acme.framework.components.Request;
import acme.framework.components.Response;
import acme.framework.entities.Principal;
import acme.framework.helpers.PrincipalHelper;
import acme.framework.services.AbstractUpdateService;
import acme.utilities.SpamModule;
import acme.utilities.SpamModule.SpamModuleResult;
import acme.utilities.SpamRepository;

@Service
public class ManagerTaskUpdateService implements AbstractUpdateService<Manager, Task> {

	// Internal state ---------------------------------------------------------

	@Autowired
	protected ManagerTaskRepository repository;
	
	@Autowired
	protected SpamRepository spamRepository;
	
	// AbstractUpdateService<Manager, Task> interface ---------------


	@Override
	public boolean authorise(final Request<Task> request) {
		assert request != null;
		
		boolean res;
		int taskId;
		final Task task;
		final Manager manager;
		Principal principal;

		taskId = request.getModel().getInteger("id");
		task = this.repository.findOneTaskById(taskId);
		manager = task.getOwner();
		principal = request.getPrincipal();
		res = manager.getUserAccount().getId() == principal.getAccountId();

		return res;
	}

	@Override
	public void bind(final Request<Task> request, final Task entity, final Errors errors) {
		assert request != null;
		assert entity != null;
		assert errors != null;

		request.bind(entity, errors, "taskId");
	}

	@Override
	public void unbind(final Request<Task> request, final Task entity, final Model model) {
		assert request != null;
		assert entity != null;
		assert model != null;

		request.unbind(entity, model, "title", "startMoment", "endMoment", "workloadHours", "workloadFraction",
			"description", "link", "isPublic");
	}

	@Override
	public Task findOne(final Request<Task> request) {
		assert request != null;

		Task result;
		int taskId;
		
		taskId = request.getModel().getInteger("id");
		
		result = this.repository.findOneTaskById(taskId);

		return result;
	}

	@Override
	public void validate(final Request<Task> request, final Task entity, final Errors errors) {
		assert request != null;
		assert entity != null;
		assert errors != null;
		
		final Date now = new Date(System.currentTimeMillis());
		
		if (!errors.hasErrors("startMoment")) {
			final Boolean isAfter = entity.getStartMoment().after(now);
			errors.state(request, isAfter, "startMoment", "manager.task.form.error.past-startMoment");
		}
		if (!errors.hasErrors("endMoment")) {
			final Boolean isAfter = entity.getEndMoment().after(now);
			errors.state(request, isAfter, "endMoment", "manager.task.form.error.past-endMoment");
		}
		if(!errors.hasErrors("endMoment") && entity.getStartMoment()!=null && entity.getEndMoment() != null) {
			final Boolean isAfter = entity.getEndMoment().after(entity.getStartMoment());
			errors.state(request, isAfter, "endMoment", "manager.task.form.error.incorrect-interval");
		}
		
		final SpamModule sm = new SpamModule(this.spamRepository);
		
		final SpamModuleResult spamResult = sm.checkSpam(entity);
		if(spamResult.isHasErrors()) {
			errors.state(request, false, "isPublic", "manager.task.form.error.spam.has-errors");
		} else if (spamResult.isSpam()){
			errors.state(request, false, "isPublic", "manager.task.form.error.spam.is-spam");
		}
		
		if(!errors.hasErrors("endMoment") && !errors.hasErrors("startMoment")) {
			final Boolean incorrectDate = this.repository.isNotPossibleModificateMoment(entity.getId(), entity.getStartMoment(), entity.getEndMoment());
			errors.state(request, !incorrectDate, "endMoment", "manager.task.form.error.incorrect-date");
		}
		
		if(!errors.hasErrors("isPublic")) {
			final Boolean notPossibleMakePrivate = this.repository.isNotPossibleMakePublic(entity.getId(), entity.getIsPublic());
			errors.state(request, !notPossibleMakePrivate, "isPublic", "manager.task.form.error.impossible-make-private");
		}
		
		
	}

	@Override
	public void update(final Request<Task> request, final Task entity) {
		assert request != null;
		assert entity != null;
		
		this.repository.save(entity);
	}

	@Override
	public void onSuccess(final Request<Task> request, final Response<Task> response) {
		assert request != null;
		assert response != null;

		if (request.isMethod(HttpMethod.POST)) {
			PrincipalHelper.handleUpdate();
		}
	}

}
